package com.example.saga.action;

import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.outbox.OutboxMessage;
import com.example.saga.outbox.OutboxMessageStatus;
import com.example.saga.outbox.OutboxService;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import com.example.saga.retry.RetryableAction;
import com.example.saga.service.SagaOrchestrationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Action for handling payment operations in the saga
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAction implements Action<SagaStates, SagaEvents> {

    private final ObjectMapper objectMapper;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final OutboxService outboxService;
    
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY = 1000; // 1 second

    @Override
    @Transactional
    public void execute(StateContext<SagaStates, SagaEvents> context) {
        try {
            SagaContext sagaContext = (SagaContext) context.getExtendedState()
                    .getVariables().get("sagaContext");
            
            // Get saga instance
            SagaInstance sagaInstance = sagaInstanceRepository.findById(sagaContext.getSagaId())
                    .orElseThrow(() -> new RuntimeException("Saga instance not found"));
            
            if (context.getTarget().getId() == SagaStates.PAYMENT_PROCESSING) {
                processPayment(sagaContext);
            } else if (context.getTarget().getId() == SagaStates.COMPENSATING_PAYMENT) {
                compensatePayment(sagaContext);
            }
            
        } catch (Exception e) {
            log.error("Error in PaymentAction", e);
            context.getExtendedState().getVariables()
                    .put("actionError", e.getMessage());
            throw new RuntimeException("Payment action failed", e);
        }
    }

    private void executeWithRetry(Runnable action) throws Exception {
        RetryableAction.builder()
                .maxRetries(MAX_RETRIES)
                .initialDelay(INITIAL_DELAY)
                .action(() -> {
                    action.run();
                    return null;
                })
                .onError(e -> log.error("Payment action failed, will retry", e))
                .onExhausted(() -> log.error("Payment action exhausted all retries"))
                .build()
                .execute();
    }

    /**
     * Process payment for an order
     */
    private void processPayment(SagaContext sagaContext) throws JsonProcessingException {
        var payload = sagaContext.getPayload();
        log.info("Processing payment for order: {}", payload.get("orderId"));
        
        // Create payment command
        Map<String, Object> paymentCommand = new HashMap<>();
        paymentCommand.put("sagaId", sagaContext.getSagaId());
        paymentCommand.put("orderId", payload.get("orderId"));
        paymentCommand.put("userId", payload.get("userId"));
        paymentCommand.put("amount", payload.get("amount"));
        paymentCommand.put("timestamp", LocalDateTime.now());
        paymentCommand.put("action", "PROCESS");
        
        // Generate a payment ID
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        paymentCommand.put("paymentId", paymentId);
        
        // Store payment ID in saga context
        payload.put("paymentId", paymentId);
        
        // Save payment command to outbox
        OutboxMessage outboxMessage = OutboxMessage.builder()
                .aggregateType("SAGA")
                .aggregateId(sagaContext.getSagaId())
                .eventType("PROCESS_PAYMENT")
                .payload(objectMapper.writeValueAsString(paymentCommand))
                .exchange(MessageConfig.SAGA_COMMAND_EXCHANGE)
                .routingKey(MessageConfig.PAYMENT_PROCESS_KEY)
                .status(OutboxMessageStatus.PENDING)
                .build();
        outboxService.saveMessage(outboxMessage);

        log.info("Payment command for saga {} saved to outbox.", sagaContext.getSagaId());
    }
    
    /**
     * Compensate payment (refund)
     */
    private void compensatePayment(SagaContext sagaContext) throws JsonProcessingException {
        var payload = sagaContext.getPayload();
        log.info("Compensating payment for order: {}", payload.get("orderId"));
        
        // Create refund command
        Map<String, Object> refundCommand = new HashMap<>();
        refundCommand.put("sagaId", sagaContext.getSagaId());
        refundCommand.put("orderId", payload.get("orderId"));
        refundCommand.put("userId", payload.get("userId"));
        refundCommand.put("paymentId", payload.get("paymentId"));
        refundCommand.put("amount", payload.get("amount"));
        refundCommand.put("timestamp", LocalDateTime.now());
        refundCommand.put("action", "REFUND");
        
        // Save refund command to outbox
        OutboxMessage outboxMessage = OutboxMessage.builder()
                .aggregateType("SAGA")
                .aggregateId(sagaContext.getSagaId())
                .eventType("COMPENSATE_PAYMENT")
                .payload(objectMapper.writeValueAsString(refundCommand))
                .exchange(MessageConfig.SAGA_COMMAND_EXCHANGE)
                .routingKey(MessageConfig.PAYMENT_REFUND_KEY)
                .status(OutboxMessageStatus.PENDING)
                .build();
        outboxService.saveMessage(outboxMessage);
        
        log.info("Refund command for saga {} saved to outbox.", sagaContext.getSagaId());
    }
} 