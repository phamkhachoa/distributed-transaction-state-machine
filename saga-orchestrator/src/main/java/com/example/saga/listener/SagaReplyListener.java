package com.example.saga.listener;

import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaContext;
import com.example.saga.service.SagaOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyListener {

    private final SagaOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MessageConfig.SAGA_REPLY_QUEUE)
    public void handleReply(Map<String, Object> reply) {
        try {
            String sagaId = (String) reply.get("sagaId");
            String status = (String) reply.get("status");
            String service = (String) reply.get("service");
            
            log.info("Received reply from {} for saga {}: {}", service, sagaId, status);
            
            SagaContext sagaContext = orchestrationService.getSagaContext(sagaId);
            
            // Map reply to appropriate event based on service and status
            switch (service.toLowerCase()) {
                case "payment":
                    handlePaymentReply(sagaId, status, reply, sagaContext);
                    break;
                    
                case "inventory":
                    handleInventoryReply(sagaId, status, reply, sagaContext);
                    break;
                    
                case "shipping":
                    handleShippingReply(sagaId, status, reply, sagaContext);
                    break;
                    
                default:
                    log.warn("Received reply from unknown service: {}", service);
            }
            
        } catch (Exception e) {
            log.error("Error processing saga reply", e);
        }
    }
    
    private void handlePaymentReply(String sagaId, String status, Map<String, Object> reply, SagaContext sagaContext) {
        switch (status.toUpperCase()) {
            case "SUCCESS":
                sagaContext.setPaymentId((String) reply.get("paymentId"));
                orchestrationService.updateSagaContext(sagaId, sagaContext);
                orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_SUCCESS);
                break;
                
            case "FAILED":
                sagaContext.setErrorMessage((String) reply.get("errorMessage"));
                orchestrationService.updateSagaContext(sagaId, sagaContext);
                orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_FAILED);
                break;
                
            case "REFUNDED":
                orchestrationService.sendEvent(sagaId, SagaEvents.PAYMENT_COMPENSATED);
                break;
                
            default:
                log.warn("Unknown payment status: {}", status);
        }
    }
    
    private void handleInventoryReply(String sagaId, String status, Map<String, Object> reply, SagaContext sagaContext) {
        switch (status.toUpperCase()) {
            case "RESERVED":
                sagaContext.setInventoryReservationId((String) reply.get("reservationId"));
                orchestrationService.updateSagaContext(sagaId, sagaContext);
                orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_RESERVED);
                break;
                
            case "INSUFFICIENT":
                sagaContext.setErrorMessage((String) reply.get("errorMessage"));
                orchestrationService.updateSagaContext(sagaId, sagaContext);
                orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_INSUFFICIENT);
                break;
                
            case "RELEASED":
                orchestrationService.sendEvent(sagaId, SagaEvents.INVENTORY_COMPENSATED);
                break;
                
            case "HEARTBEAT":
                log.debug("Received heartbeat from inventory for saga: {}", sagaId);
                break;
                
            default:
                log.warn("Unknown inventory status: {}", status);
        }
    }
    
    private void handleShippingReply(String sagaId, String status, Map<String, Object> reply, SagaContext sagaContext) {
        switch (status.toUpperCase()) {
            case "SCHEDULED":
                sagaContext.setShippingId((String) reply.get("shippingId"));
                orchestrationService.updateSagaContext(sagaId, sagaContext);
                orchestrationService.sendEvent(sagaId, SagaEvents.SHIPPING_SCHEDULED);
                break;
                
            case "FAILED":
                sagaContext.setErrorMessage((String) reply.get("errorMessage"));
                orchestrationService.updateSagaContext(sagaId, sagaContext);
                orchestrationService.sendEvent(sagaId, SagaEvents.SHIPPING_FAILED);
                break;
                
            case "CANCELLED":
                orchestrationService.sendEvent(sagaId, SagaEvents.SHIPPING_COMPENSATED);
                break;
                
            case "STATUS_UPDATE":
                String shippingStatus = (String) reply.get("shippingStatus");
                sagaContext.addMetadata("lastShippingStatus", shippingStatus);
                sagaContext.addMetadata("lastShippingUpdate", reply.get("timestamp"));
                orchestrationService.updateSagaContext(sagaId, sagaContext);
                log.info("Updated shipping status for saga {}: {}", sagaId, shippingStatus);
                break;
                
            default:
                log.warn("Unknown shipping status: {}", status);
        }
    }
} 