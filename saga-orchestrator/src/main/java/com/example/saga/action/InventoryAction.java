package com.example.saga.action;

import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.service.SagaOrchestrationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Action for handling inventory operations in the saga
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryAction implements Action<SagaStates, SagaEvents> {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final SagaOrchestrationService orchestrationService;
    
    // Heartbeat interval for long-running inventory operations
    private static final long HEARTBEAT_INTERVAL_MINUTES = 5;
    private static final long INVENTORY_CHECK_TIMEOUT_MINUTES = 10;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Override
    public void execute(StateContext<SagaStates, SagaEvents> context) {
        try {
            SagaContext sagaContext = (SagaContext) context.getExtendedState()
                    .getVariables().get("sagaContext");
            
            if (context.getTarget().getId() == SagaStates.INVENTORY_RESERVING) {
                reserveInventory(sagaContext);
            } else if (context.getTarget().getId() == SagaStates.COMPENSATING_INVENTORY) {
                releaseInventory(sagaContext);
            }
        } catch (Exception e) {
            log.error("Error in InventoryAction", e);
            throw new RuntimeException("Inventory action failed", e);
        }
    }

    /**
     * Reserve inventory for an order
     */
    private void reserveInventory(SagaContext sagaContext) throws JsonProcessingException {
        log.info("Reserving inventory for order: {}", sagaContext.getOrderId());
        
        // Create inventory command
        Map<String, Object> inventoryCommand = new HashMap<>();
        inventoryCommand.put("sagaId", sagaContext.getSagaId());
        inventoryCommand.put("orderId", sagaContext.getOrderId());
        inventoryCommand.put("products", sagaContext.getProducts());
        inventoryCommand.put("timestamp", LocalDateTime.now());
        inventoryCommand.put("action", "RESERVE");
        
        // Send inventory command
        rabbitTemplate.convertAndSend(
            MessageConfig.SAGA_COMMAND_EXCHANGE,
            MessageConfig.INVENTORY_RESERVE_KEY,
            objectMapper.writeValueAsString(inventoryCommand)
        );
        
        log.info("Inventory reserve command sent for saga: {}, order: {}", 
                sagaContext.getSagaId(), sagaContext.getOrderId());

        // Start heartbeat monitoring and timeout check
        startHeartbeatMonitoring(sagaContext);
        scheduleInventoryTimeout(sagaContext);
    }

    /**
     * Release inventory (compensation)
     */
    private void releaseInventory(SagaContext sagaContext) throws JsonProcessingException {
        log.info("Releasing inventory for order: {}", sagaContext.getOrderId());
        
        // Create release command
        Map<String, Object> releaseCommand = new HashMap<>();
        releaseCommand.put("sagaId", sagaContext.getSagaId());
        releaseCommand.put("orderId", sagaContext.getOrderId());
        releaseCommand.put("inventoryReservationId", sagaContext.getInventoryReservationId());
        releaseCommand.put("products", sagaContext.getProducts());
        releaseCommand.put("timestamp", LocalDateTime.now());
        releaseCommand.put("action", "RELEASE");
        
        // Send release command
        rabbitTemplate.convertAndSend(
            MessageConfig.SAGA_COMMAND_EXCHANGE,
            MessageConfig.INVENTORY_RELEASE_KEY,
            objectMapper.writeValueAsString(releaseCommand)
        );
        
        log.info("Inventory release command sent for saga: {}, order: {}", 
                sagaContext.getSagaId(), sagaContext.getOrderId());
    }

    private void startHeartbeatMonitoring(SagaContext sagaContext) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Check current state
                SagaContext currentContext = orchestrationService.getSagaContext(sagaContext.getSagaId());
                SagaStates currentState = orchestrationService.getCurrentState(sagaContext.getSagaId());
                
                // Only continue heartbeat if still in INVENTORY_RESERVING state
                if (currentState == SagaStates.INVENTORY_RESERVING) {
                    Map<String, Object> heartbeatCommand = new HashMap<>();
                    heartbeatCommand.put("sagaId", sagaContext.getSagaId());
                    heartbeatCommand.put("orderId", sagaContext.getOrderId());
                    heartbeatCommand.put("timestamp", LocalDateTime.now());
                    heartbeatCommand.put("action", "HEARTBEAT");

                    rabbitTemplate.convertAndSend(
                        MessageConfig.SAGA_COMMAND_EXCHANGE,
                        MessageConfig.INVENTORY_RESERVE_KEY + ".heartbeat",
                        heartbeatCommand
                    );
                    
                    log.debug("Sent heartbeat for inventory reservation - saga: {}, order: {}", 
                            sagaContext.getSagaId(), sagaContext.getOrderId());
                } else {
                    // Stop heartbeat if no longer in reserving state
                    throw new InterruptedException("Inventory reservation completed or failed");
                }
            } catch (Exception e) {
                log.error("Error in inventory heartbeat", e);
                Thread.currentThread().interrupt();
            }
        }, HEARTBEAT_INTERVAL_MINUTES, HEARTBEAT_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void scheduleInventoryTimeout(SagaContext sagaContext) {
        scheduler.schedule(() -> {
            try {
                // Check if still in inventory reserving state
                SagaContext currentContext = orchestrationService.getSagaContext(sagaContext.getSagaId());
                if (currentContext.getStatus().equals("IN_PROGRESS") && 
                    orchestrationService.getCurrentState(sagaContext.getSagaId()) == SagaStates.INVENTORY_RESERVING) {
                    
                    log.warn("Inventory reservation timeout for saga: {}, order: {}", 
                            sagaContext.getSagaId(), sagaContext.getOrderId());
                    
                    // Send timeout event
                    orchestrationService.sendEvent(sagaContext.getSagaId(), SagaEvents.INVENTORY_TIMEOUT);
                }
            } catch (Exception e) {
                log.error("Error processing inventory timeout", e);
            }
        }, INVENTORY_CHECK_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }
} 