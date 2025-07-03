package com.example.saga.action;

import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.outbox.OutboxMessage;
import com.example.saga.outbox.OutboxMessageStatus;
import com.example.saga.outbox.OutboxService;
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

    private final ObjectMapper objectMapper;
    private final SagaOrchestrationService orchestrationService;
    private final OutboxService outboxService;
    
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
        var payload = sagaContext.getPayload();
        log.info("Reserving inventory for order: {}", payload.get("orderId"));
        
        // Create inventory command
        Map<String, Object> inventoryCommand = new HashMap<>();
        inventoryCommand.put("sagaId", sagaContext.getSagaId());
        inventoryCommand.put("orderId", payload.get("orderId"));
        inventoryCommand.put("products", payload.get("products"));
        inventoryCommand.put("timestamp", LocalDateTime.now());
        inventoryCommand.put("action", "RESERVE");
        
        // Save inventory command to outbox
        OutboxMessage outboxMessage = OutboxMessage.builder()
                .aggregateType("SAGA")
                .aggregateId(sagaContext.getSagaId())
                .eventType("RESERVE_INVENTORY")
                .payload(objectMapper.writeValueAsString(inventoryCommand))
                .exchange(MessageConfig.SAGA_COMMAND_EXCHANGE)
                .routingKey(MessageConfig.INVENTORY_RESERVE_KEY)
                .status(OutboxMessageStatus.PENDING)
                .build();
        outboxService.saveMessage(outboxMessage);
        
        log.info("Inventory reserve command for saga {} saved to outbox.", sagaContext.getSagaId());

        // Start heartbeat monitoring and timeout check
        startHeartbeatMonitoring(sagaContext);
        scheduleInventoryTimeout(sagaContext);
    }

    /**
     * Release inventory (compensation)
     */
    private void releaseInventory(SagaContext sagaContext) throws JsonProcessingException {
        var payload = sagaContext.getPayload();
        log.info("Releasing inventory for order: {}", payload.get("orderId"));
        
        // Create release command
        Map<String, Object> releaseCommand = new HashMap<>();
        releaseCommand.put("sagaId", sagaContext.getSagaId());
        releaseCommand.put("orderId", payload.get("orderId"));
        releaseCommand.put("inventoryReservationId", payload.get("inventoryReservationId"));
        releaseCommand.put("products", payload.get("products"));
        releaseCommand.put("timestamp", LocalDateTime.now());
        releaseCommand.put("action", "RELEASE");
        
        // Save release command to outbox
        OutboxMessage outboxMessage = OutboxMessage.builder()
                .aggregateType("SAGA")
                .aggregateId(sagaContext.getSagaId())
                .eventType("RELEASE_INVENTORY")
                .payload(objectMapper.writeValueAsString(releaseCommand))
                .exchange(MessageConfig.SAGA_COMMAND_EXCHANGE)
                .routingKey(MessageConfig.INVENTORY_RELEASE_KEY)
                .status(OutboxMessageStatus.PENDING)
                .build();
        outboxService.saveMessage(outboxMessage);
        
        log.info("Inventory release command for saga {} saved to outbox.", sagaContext.getSagaId());
    }

    private void startHeartbeatMonitoring(SagaContext sagaContext) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Check current state
                SagaContext currentContext = (SagaContext) orchestrationService.getSagaContext(sagaContext.getSagaId());
                SagaStates currentState = orchestrationService.getCurrentState(sagaContext.getSagaId());
                
                // Only continue heartbeat if still in INVENTORY_RESERVING state
                if (currentState == SagaStates.INVENTORY_RESERVING) {
                    Map<String, Object> heartbeatCommand = new HashMap<>();
                    heartbeatCommand.put("sagaId", sagaContext.getSagaId());
                    heartbeatCommand.put("orderId", sagaContext.getPayload().get("orderId"));
                    heartbeatCommand.put("timestamp", LocalDateTime.now());
                    heartbeatCommand.put("action", "HEARTBEAT");

                    // Heartbeat is not critical, so we can send it directly.
                    // Alternatively, it could also go to the outbox with a lower priority.
                    // For now, sending directly is acceptable.
                    new RabbitTemplate().convertAndSend(
                        MessageConfig.SAGA_COMMAND_EXCHANGE,
                        MessageConfig.INVENTORY_RESERVE_KEY + ".heartbeat",
                        heartbeatCommand
                    );
                    
                    log.debug("Sent heartbeat for inventory reservation - saga: {}, order: {}", 
                            sagaContext.getSagaId(), sagaContext.getPayload().get("orderId"));
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
                SagaContext currentContext = (SagaContext) orchestrationService.getSagaContext(sagaContext.getSagaId());
                String status = (String) currentContext.getPayload().get("status");

                if ("IN_PROGRESS".equals(status) &&
                    orchestrationService.getCurrentState(sagaContext.getSagaId()) == SagaStates.INVENTORY_RESERVING) {
                    
                    log.warn("Inventory reservation timeout for saga: {}, order: {}", 
                            sagaContext.getSagaId(), sagaContext.getPayload().get("orderId"));
                    
                    // Send timeout event
                    orchestrationService.sendEvent(sagaContext.getSagaId(), SagaEvents.INVENTORY_TIMEOUT);
                }
            } catch (Exception e) {
                log.error("Error processing inventory timeout", e);
            }
        }, INVENTORY_CHECK_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }
} 