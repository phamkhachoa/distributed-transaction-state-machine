package com.example.saga.listener;

import com.example.common.dto.SagaReply;
import com.example.saga.idempotency.IdempotencyService;
import com.example.saga.idempotency.ProcessedMessage;
import com.example.saga.idempotency.ProcessedMessageRepository;
import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.service.SagaOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Listener for saga reply messages from external services
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyListener {

    private final SagaOrchestrationService orchestrationService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    
    @RabbitListener(queues = MessageConfig.PAYMENT_REPLY_QUEUE)
    @Transactional
    public void handlePaymentReply(String message) {
        try {
            SagaReply reply = objectMapper.readValue(message, SagaReply.class);
            String sagaId = reply.getSagaId();
            String requestId = reply.getRequestId();
            boolean success = reply.isSuccess();
            
            // Kiểm tra xem reply đã được xử lý chưa
            if (processedMessageRepository.existsByRequestId(requestId)) {
                log.info("Duplicate reply detected: {} for saga: {}", requestId, sagaId);
                return;
            }
            
            log.info("Received payment reply for saga: {}, success: {}", sagaId, success);
            
            // Xử lý reply trong transaction idempotent
            idempotencyService.executeWithIdempotency(
                    requestId,
                    sagaId,
                    "saga-orchestrator",
                    "handle-payment-reply",
                    () -> {
                        // Get saga context
                        SagaContext sagaContext = (SagaContext) orchestrationService.getSagaContext(sagaId);
                        if (sagaContext == null) {
                            log.warn("No saga context found for payment reply: {}", sagaId);
                            return null;
                        }
                        
                        // Update payment information
                        Map<String, Object> payload = reply.getPayload();
                        if (payload != null && payload.containsKey("paymentId")) {
                            sagaContext.setPaymentId(payload.get("paymentId").toString());
                        }
                        
                        // Update saga context
                        orchestrationService.updateSagaContext(sagaId, sagaContext);
                        
                        // Send appropriate event based on status
                        if (success) {
                            orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_SUCCESS);
                        } else {
                            orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_FAILED);
                        }
                        
                        return true;
                    });
            
        } catch (Exception e) {
            log.error("Error processing payment reply", e);
        }
    }
    
    @RabbitListener(queues = MessageConfig.PAYMENT_REPLY_QUEUE)
    @Transactional
    public void handlePaymentCompensationReply(String message) {
        try {
            SagaReply reply = objectMapper.readValue(message, SagaReply.class);
            String sagaId = reply.getSagaId();
            String requestId = reply.getRequestId();
            boolean success = reply.isSuccess();
            String action = (String) reply.getPayload().get("action");
            
            // Kiểm tra xem reply đã được xử lý chưa
            if (processedMessageRepository.existsByRequestId(requestId)) {
                log.info("Duplicate compensation reply detected: {} for saga: {}", requestId, sagaId);
                return;
            }
            
            if ("REFUND".equals(action)) {
                log.info("Received payment refund reply for saga: {}, success: {}", sagaId, success);
                
                // Xử lý reply trong transaction idempotent
                idempotencyService.executeWithIdempotency(
                        requestId,
                        sagaId,
                        "saga-orchestrator",
                        "handle-payment-compensation-reply",
                        () -> {
                            if (success) {
                                orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_COMPENSATED);
                            } else {
                                log.error("Payment refund failed for saga: {}", sagaId);
                            }
                            return true;
                        });
            }
            
        } catch (Exception e) {
            log.error("Error processing payment compensation reply", e);
        }
    }
    
    @RabbitListener(queues = MessageConfig.INVENTORY_REPLY_QUEUE)
    @Transactional
    public void handleInventoryReply(String message) {
        try {
            SagaReply reply = objectMapper.readValue(message, SagaReply.class);
            String sagaId = reply.getSagaId();
            String requestId = reply.getRequestId();
            boolean success = reply.isSuccess();
            
            // Kiểm tra xem reply đã được xử lý chưa
            if (processedMessageRepository.existsByRequestId(requestId)) {
                log.info("Duplicate reply detected: {} for saga: {}", requestId, sagaId);
                return;
            }
            
            log.info("Received inventory reply for saga: {}, success: {}", sagaId, success);
            
            // Xử lý reply trong transaction idempotent
            idempotencyService.executeWithIdempotency(
                    requestId,
                    sagaId,
                    "saga-orchestrator",
                    "handle-inventory-reply",
                    () -> {
                        // Get saga context
                        SagaContext sagaContext = (SagaContext) orchestrationService.getSagaContext(sagaId);
                        if (sagaContext == null) {
                            log.warn("No saga context found for inventory reply: {}", sagaId);
                            return null;
                        }
                        
                        // Update inventory information
                        Map<String, Object> payload = reply.getPayload();
                        if (payload != null && payload.containsKey("inventoryReservationId")) {
                            sagaContext.setReservationId(payload.get("inventoryReservationId").toString());
                        }
                        
                        // Update saga context
                        orchestrationService.updateSagaContext(sagaId, sagaContext);
                        
                        // Send appropriate event based on status
                        if (success) {
                            orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_RESERVED);
                        } else {
                            String reason = reply.getReason();
                            if ("INSUFFICIENT".equals(reason)) {
                                orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_INSUFFICIENT);
                            } else {
                                orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_FAILED);
                            }
                        }
                        
                        return true;
                    });
            
        } catch (Exception e) {
            log.error("Error processing inventory reply", e);
        }
    }
    
    @RabbitListener(queues = MessageConfig.INVENTORY_REPLY_QUEUE)
    public void handleInventoryCompensationReply(String message) {
        try {
            Map<String, Object> reply = objectMapper.readValue(message, Map.class);
            String sagaId = (String) reply.get("sagaId");
            String status = (String) reply.get("status");
            String action = (String) reply.get("action");
            
            if ("RELEASE".equals(action)) {
                log.info("Received inventory release reply for saga: {}, status: {}", sagaId, status);
                
                if ("SUCCESS".equals(status)) {
                    orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_COMPENSATED);
                } else {
                    log.error("Inventory release failed for saga: {}", sagaId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing inventory compensation reply", e);
        }
    }
    
    @RabbitListener(queues = MessageConfig.SHIPPING_REPLY_QUEUE)
    public void handleShippingReply(String message) {
        try {
            Map<String, Object> reply = objectMapper.readValue(message, Map.class);
            String sagaId = (String) reply.get("sagaId");
            String status = (String) reply.get("status");
            
            log.info("Received shipping reply for saga: {}, status: {}", sagaId, status);
            
            // Get saga context
            SagaContext sagaContext = (SagaContext) orchestrationService.getSagaContext(sagaId);
            if (sagaContext == null) {
                log.warn("No saga context found for shipping reply: {}", sagaId);
                return;
            }
            
            // Update shipping information
            if (reply.containsKey("shippingId")) {
                sagaContext.getPayload().put("shippingId", reply.get("shippingId"));
            }
            
            // Update saga context
            orchestrationService.updateSagaContext(sagaId, sagaContext);
            
            // Send appropriate event based on status
            if ("SUCCESS".equals(status)) {
                orchestrationService.sendEvent(sagaId, SagaEvents.SHIPPING_SCHEDULED);
            } else if ("FAILED".equals(status)) {
                orchestrationService.sendEvent(sagaId, SagaEvents.SHIPPING_FAILED);
            }
            
        } catch (Exception e) {
            log.error("Error processing shipping reply", e);
        }
    }
    
    @RabbitListener(queues = MessageConfig.SHIPPING_REPLY_QUEUE)
    public void handleShippingCompensationReply(String message) {
        try {
            Map<String, Object> reply = objectMapper.readValue(message, Map.class);
            String sagaId = (String) reply.get("sagaId");
            String status = (String) reply.get("status");
            String action = (String) reply.get("action");
            
            if ("CANCEL".equals(action)) {
                log.info("Received shipping cancellation reply for saga: {}, status: {}", sagaId, status);
                
                if ("SUCCESS".equals(status)) {
                    orchestrationService.sendEvent(sagaId, SagaEvents.SHIPPING_COMPENSATED);
                } else {
                    log.error("Shipping cancellation failed for saga: {}", sagaId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing shipping compensation reply", e);
        }
    }
} 