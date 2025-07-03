package com.example.saga.listener;

import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.service.SagaOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listener for saga reply messages from external services
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyListener {

    private final SagaOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;
    
    @RabbitListener(queues = MessageConfig.PAYMENT_REPLY_QUEUE)
    public void handlePaymentReply(String message) {
        try {
            Map<String, Object> reply = objectMapper.readValue(message, Map.class);
            String sagaId = (String) reply.get("sagaId");
            String status = (String) reply.get("status");
            
            log.info("Received payment reply for saga: {}, status: {}", sagaId, status);
            
            // Get saga context
            SagaContext sagaContext = (SagaContext) orchestrationService.getSagaContext(sagaId);
            if (sagaContext == null) {
                log.warn("No saga context found for payment reply: {}", sagaId);
                return;
            }
            
            // Update payment information
            if (reply.containsKey("paymentId")) {
                sagaContext.getPayload().put("paymentId", reply.get("paymentId"));
            }
            
            // Update saga context
            orchestrationService.updateSagaContext(sagaId, sagaContext);
            
            // Send appropriate event based on status
            if ("SUCCESS".equals(status)) {
                orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_SUCCESS);
            } else if ("FAILED".equals(status)) {
                orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_FAILED);
            } else if ("TIMEOUT".equals(status)) {
                orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_TIMEOUT);
            }
            
        } catch (Exception e) {
            log.error("Error processing payment reply", e);
        }
    }
    
    @RabbitListener(queues = MessageConfig.PAYMENT_REPLY_QUEUE)
    public void handlePaymentCompensationReply(String message) {
        try {
            Map<String, Object> reply = objectMapper.readValue(message, Map.class);
            String sagaId = (String) reply.get("sagaId");
            String status = (String) reply.get("status");
            String action = (String) reply.get("action");
            
            if ("REFUND".equals(action)) {
                log.info("Received payment refund reply for saga: {}, status: {}", sagaId, status);
                
                if ("SUCCESS".equals(status)) {
                    orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_COMPENSATED);
                } else {
                    log.error("Payment refund failed for saga: {}", sagaId);
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing payment compensation reply", e);
        }
    }
    
    @RabbitListener(queues = MessageConfig.INVENTORY_REPLY_QUEUE)
    public void handleInventoryReply(String message) {
        try {
            Map<String, Object> reply = objectMapper.readValue(message, Map.class);
            String sagaId = (String) reply.get("sagaId");
            String status = (String) reply.get("status");
            
            log.info("Received inventory reply for saga: {}, status: {}", sagaId, status);
            
            // Get saga context
            SagaContext sagaContext = (SagaContext) orchestrationService.getSagaContext(sagaId);
            if (sagaContext == null) {
                log.warn("No saga context found for inventory reply: {}", sagaId);
                return;
            }
            
            // Update inventory information
            if (reply.containsKey("inventoryReservationId")) {
                sagaContext.getPayload().put("inventoryReservationId", reply.get("inventoryReservationId"));
            }
            
            // Update saga context
            orchestrationService.updateSagaContext(sagaId, sagaContext);
            
            // Send appropriate event based on status
            if ("SUCCESS".equals(status)) {
                orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_RESERVED);
            } else if ("INSUFFICIENT".equals(status)) {
                orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_INSUFFICIENT);
            } else if ("FAILED".equals(status)) {
                orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_FAILED);
            }
            
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