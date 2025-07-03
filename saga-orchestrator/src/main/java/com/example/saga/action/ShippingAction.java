package com.example.saga.action;

import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.model.ShippingContext;
import com.example.saga.model.ShippingEvents;
import com.example.saga.service.SagaOrchestrationService;
import com.example.saga.service.ShippingSagaService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingAction implements Action<SagaStates, SagaEvents> {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final SagaOrchestrationService orchestrationService;
    private final ShippingSagaService shippingSagaService;
    
    // Constants for long-running shipping process
    private static final long HEARTBEAT_INTERVAL_HOURS = 4; // Check every 4 hours
    private static final long INITIAL_STATUS_CHECK_MINUTES = 30; // First status check after 30 minutes
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @Override
    public void execute(StateContext<SagaStates, SagaEvents> context) {
        try {
            SagaContext sagaContext = (SagaContext) context.getExtendedState()
                    .getVariables().get("sagaContext");
            
            if (context.getTarget().getId() == SagaStates.SHIPPING_SCHEDULING) {
                initiateShipping(sagaContext);
            } else if (context.getTarget().getId() == SagaStates.COMPENSATING_SHIPPING) {
                cancelShipping(sagaContext);
            }
        } catch (Exception e) {
            log.error("Error in ShippingAction", e);
            throw new RuntimeException("Shipping action failed", e);
        }
    }

    private void initiateShipping(SagaContext sagaContext) {
        // Create ShippingContext from SagaContext
        ShippingContext shippingContext = ShippingContext.builder()
                .orderId(sagaContext.getOrderId())
                .shippingAddress(sagaContext.getShippingAddress())
                .startTime(LocalDateTime.now())
                .build();
        
        // Start shipping saga
        String shippingSagaId = shippingSagaService.startShippingSaga(shippingContext);
        
        // Store shipping saga ID in order saga context
        sagaContext.setShippingSagaId(shippingSagaId);
        orchestrationService.updateSagaContext(sagaContext.getSagaId(), sagaContext);
        
        log.info("Started shipping saga {} for order saga {}", 
                shippingSagaId, sagaContext.getSagaId());

        // Send shipping command to the shipping service
        Map<String, Object> shippingCommand = new HashMap<>();
        shippingCommand.put("sagaId", sagaContext.getSagaId());
        shippingCommand.put("shippingSagaId", shippingSagaId);
        shippingCommand.put("orderId", sagaContext.getOrderId());
        shippingCommand.put("userId", sagaContext.getUserId());
        shippingCommand.put("products", sagaContext.getProducts());
        shippingCommand.put("timestamp", LocalDateTime.now());
        shippingCommand.put("action", "SCHEDULE");

        // Send shipping command
        rabbitTemplate.convertAndSend(
            MessageConfig.SAGA_COMMAND_EXCHANGE,
            MessageConfig.SHIPPING_SCHEDULE_KEY,
            shippingCommand
        );
        
        log.info("Shipping command sent for saga: {}, order: {}", 
                sagaContext.getSagaId(), sagaContext.getOrderId());

        // Start monitoring process
        startLongRunningShippingMonitoring(sagaContext);
    }

    private void cancelShipping(SagaContext sagaContext) {
        // If there's a shipping saga, cancel it
        if (sagaContext.getShippingSagaId() != null) {
            orchestrationService.sendEvent(
                sagaContext.getShippingSagaId(), 
                ShippingEvents.CANCEL_SHIPPING
            );
            log.info("Sent cancel event to shipping saga: {}", sagaContext.getShippingSagaId());
        }

        Map<String, Object> cancelCommand = new HashMap<>();
        cancelCommand.put("sagaId", sagaContext.getSagaId());
        cancelCommand.put("orderId", sagaContext.getOrderId());
        cancelCommand.put("shippingId", sagaContext.getShippingId());
        cancelCommand.put("timestamp", LocalDateTime.now());
        cancelCommand.put("action", "CANCEL");

        rabbitTemplate.convertAndSend(
            MessageConfig.SAGA_COMMAND_EXCHANGE,
            MessageConfig.SHIPPING_CANCEL_KEY,
            cancelCommand
        );
        
        log.info("Shipping cancellation command sent for saga: {}, order: {}", 
                sagaContext.getSagaId(), sagaContext.getOrderId());
    }
    
    /**
     * Start monitoring for the long-running shipping process
     */
    private void startLongRunningShippingMonitoring(SagaContext sagaContext) {
        // Schedule initial status check
        scheduler.schedule(() -> {
            checkShippingStatus(sagaContext);
        }, INITIAL_STATUS_CHECK_MINUTES, TimeUnit.MINUTES);
        
        log.info("Scheduled initial shipping status check in {} minutes for saga: {}", 
                INITIAL_STATUS_CHECK_MINUTES, sagaContext.getSagaId());
    }
    
    /**
     * Check shipping status and schedule next check
     */
    private void checkShippingStatus(SagaContext sagaContext) {
        try {
            // Get updated saga context
            SagaContext updatedContext = (SagaContext) 
                    orchestrationService.getSagaContext(sagaContext.getSagaId());
            
            // Check if shipping saga is still active
            if (updatedContext.getShippingSagaId() != null) {
                // Get shipping saga status
                Object shippingContext = 
                        orchestrationService.getSagaContext(updatedContext.getShippingSagaId());
                
                // Log current status
                log.info("Shipping status check for saga: {}, shipping saga: {}", 
                        sagaContext.getSagaId(), updatedContext.getShippingSagaId());
                
                // Schedule next check
                scheduler.schedule(() -> {
                    checkShippingStatus(updatedContext);
                }, HEARTBEAT_INTERVAL_HOURS, TimeUnit.HOURS);
            } else {
                log.info("Shipping monitoring completed for saga: {}", sagaContext.getSagaId());
            }
        } catch (Exception e) {
            log.error("Error checking shipping status for saga: {}", sagaContext.getSagaId(), e);
        }
    }
    
    /**
     * Handle shipping saga completion event and update the order saga
     */
    public void handleShippingCompletion(String orderSagaId, String shippingSagaId, boolean success) {
        try {
            // Get order saga context
            SagaContext sagaContext = (SagaContext) orchestrationService.getSagaContext(orderSagaId);
            
            // Verify this is the correct shipping saga
            if (!shippingSagaId.equals(sagaContext.getShippingSagaId())) {
                log.warn("Shipping saga ID mismatch for order saga {}: expected {}, got {}", 
                        orderSagaId, sagaContext.getShippingSagaId(), shippingSagaId);
                return;
            }
            
            // Send appropriate event to order saga
            if (success) {
                orchestrationService.sendEvent(orderSagaId, SagaEvents.SHIPPING_SCHEDULED);
                log.info("Shipping completed successfully for order saga: {}", orderSagaId);
            } else {
                orchestrationService.sendEvent(orderSagaId, SagaEvents.SHIPPING_FAILED);
                log.warn("Shipping failed for order saga: {}", orderSagaId);
            }
        } catch (Exception e) {
            log.error("Error handling shipping completion for order saga: {}", orderSagaId, e);
        }
    }
} 