package com.example.saga.action;

import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.model.ShippingContext;
import com.example.saga.model.ShippingEvents;
import com.example.saga.outbox.OutboxMessage;
import com.example.saga.outbox.OutboxMessageStatus;
import com.example.saga.outbox.OutboxService;
import com.example.saga.service.SagaOrchestrationService;
import com.example.saga.service.ShippingSagaService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingAction implements Action<SagaStates, SagaEvents> {

    private final ObjectMapper objectMapper;
    private final SagaOrchestrationService orchestrationService;
    private final ShippingSagaService shippingSagaService;
    private final OutboxService outboxService;
    
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
        var payload = sagaContext.getPayload();
        // Create ShippingContext from SagaContext
        ShippingContext shippingContext = ShippingContext.builder()
                .orderId(String.valueOf(payload.get("orderId")))
                .shippingAddress((String) payload.get("shippingAddress"))
                .startTime(LocalDateTime.now())
                .build();
        
        // Start shipping saga
        String shippingSagaId = shippingSagaService.startShippingSaga(shippingContext);
        
        // Store shipping saga ID in order saga context
        payload.put("shippingSagaId", shippingSagaId);
        orchestrationService.updateSagaContext(sagaContext.getSagaId(), sagaContext);
        
        log.info("Started shipping saga {} for order saga {}", 
                shippingSagaId, sagaContext.getSagaId());

        // Send shipping command to the shipping service
        Map<String, Object> shippingCommand = new HashMap<>();
        shippingCommand.put("sagaId", sagaContext.getSagaId());
        shippingCommand.put("shippingSagaId", shippingSagaId);
        shippingCommand.put("orderId", payload.get("orderId"));
        shippingCommand.put("userId", payload.get("userId"));
        shippingCommand.put("products", payload.get("products"));
        shippingCommand.put("timestamp", LocalDateTime.now());
        shippingCommand.put("action", "SCHEDULE");

        // Save shipping command to outbox
        try {
            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .aggregateType("SAGA")
                    .aggregateId(sagaContext.getSagaId())
                    .eventType("SCHEDULE_SHIPPING")
                    .payload(objectMapper.writeValueAsString(shippingCommand))
                    .exchange(MessageConfig.SAGA_COMMAND_EXCHANGE)
                    .routingKey(MessageConfig.SHIPPING_SCHEDULE_KEY)
                    .status(OutboxMessageStatus.PENDING)
                    .build();
            outboxService.saveMessage(outboxMessage);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize shipping command for outbox", e);
            throw new RuntimeException(e);
        }

        log.info("Shipping command for saga {} saved to outbox.", sagaContext.getSagaId());

        // Start monitoring process
        startLongRunningShippingMonitoring(sagaContext);
    }

    private void cancelShipping(SagaContext sagaContext) {
        var payload = sagaContext.getPayload();
        // If there's a shipping saga, cancel it
        if (payload.get("shippingSagaId") != null) {
            orchestrationService.sendEvent(
                (String) payload.get("shippingSagaId"),
                ShippingEvents.CANCEL_SHIPPING
            );
            log.info("Sent cancel event to shipping saga: {}", payload.get("shippingSagaId"));
        }

        Map<String, Object> cancelCommand = new HashMap<>();
        cancelCommand.put("sagaId", sagaContext.getSagaId());
        cancelCommand.put("orderId", payload.get("orderId"));
        cancelCommand.put("shippingId", payload.get("shippingId"));
        cancelCommand.put("timestamp", LocalDateTime.now());
        cancelCommand.put("action", "CANCEL");

        try {
            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .aggregateType("SAGA")
                    .aggregateId(sagaContext.getSagaId())
                    .eventType("CANCEL_SHIPPING")
                    .payload(objectMapper.writeValueAsString(cancelCommand))
                    .exchange(MessageConfig.SAGA_COMMAND_EXCHANGE)
                    .routingKey(MessageConfig.SHIPPING_CANCEL_KEY)
                    .status(OutboxMessageStatus.PENDING)
                    .build();
            outboxService.saveMessage(outboxMessage);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize shipping cancel command for outbox", e);
            throw new RuntimeException(e);
        }
        
        log.info("Shipping cancellation command for saga {} saved to outbox.", sagaContext.getSagaId());
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
            if (updatedContext.getPayload().get("shippingSagaId") != null) {
                // Get shipping saga status
                Object shippingContext =
                        orchestrationService.getSagaContext((String) updatedContext.getPayload().get("shippingSagaId"));
                
                // Log current status
                log.info("Shipping status check for saga: {}, shipping saga: {}", 
                        sagaContext.getSagaId(), updatedContext.getPayload().get("shippingSagaId"));
                
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
            if (!shippingSagaId.equals(sagaContext.getPayload().get("shippingSagaId"))) {
                log.warn("Shipping saga ID mismatch for order saga {}: expected {}, got {}", 
                        orderSagaId, sagaContext.getPayload().get("shippingSagaId"), shippingSagaId);
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