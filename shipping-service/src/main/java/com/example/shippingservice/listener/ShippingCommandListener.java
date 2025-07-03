package com.example.shippingservice.listener;

import com.example.shippingservice.config.RabbitConfig;
import com.example.shippingservice.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingCommandListener {

    private final ShippingService shippingService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.SHIPPING_COMMAND_QUEUE)
    public void handleCommand(Map<String, Object> command) {
        String sagaId = (String) command.get("sagaId");
        String orderId = (String) command.get("orderId");
        String action = (String) command.get("action");

        log.info("Received shipping command: {} for saga: {}, order: {}", action, sagaId, orderId);

        try {
            Map<String, Object> reply = new HashMap<>();
            reply.put("sagaId", sagaId);
            reply.put("orderId", orderId);
            reply.put("service", "shipping");

            switch (action.toUpperCase()) {
                case "SCHEDULE":
                    handleSchedule(command, reply);
                    break;
                    
                case "CANCEL":
                    handleCancel(command, reply);
                    break;
                    
                case "STATUS_CHECK":
                    handleStatusCheck(command, reply);
                    break;
                    
                default:
                    reply.put("status", "FAILED");
                    reply.put("errorMessage", "Unknown action: " + action);
            }

            // Send reply
            rabbitTemplate.convertAndSend(
                RabbitConfig.SAGA_REPLY_EXCHANGE,
                "saga.reply." + action.toLowerCase(),
                reply
            );

        } catch (Exception e) {
            log.error("Error processing shipping command", e);
            sendErrorReply(sagaId, orderId, action, e.getMessage());
        }
    }

    private void handleSchedule(Map<String, Object> command, Map<String, Object> reply) {
        String orderId = (String) command.get("orderId");
        String userId = (String) command.get("userId");
        @SuppressWarnings("unchecked")
        Map<String, Integer> products = (Map<String, Integer>) command.get("products");

        String shippingId = shippingService.scheduleShipment(orderId, userId, products);

        if (shippingId != null) {
            reply.put("status", "SCHEDULED");
            reply.put("shippingId", shippingId);
            
            // Initial shipping status
            reply.put("shippingStatus", "PROCESSING");
            reply.put("estimatedDelivery", shippingService.getEstimatedDeliveryDate(shippingId));
        } else {
            reply.put("status", "FAILED");
            reply.put("errorMessage", "Failed to schedule shipment");
        }
    }

    private void handleCancel(Map<String, Object> command, Map<String, Object> reply) {
        String orderId = (String) command.get("orderId");
        String shippingId = (String) command.get("shippingId");

        boolean cancelled = shippingService.cancelShipment(orderId, shippingId);

        if (cancelled) {
            reply.put("status", "CANCELLED");
        } else {
            reply.put("status", "FAILED");
            reply.put("errorMessage", "Failed to cancel shipment");
        }
    }

    private void handleStatusCheck(Map<String, Object> command, Map<String, Object> reply) {
        String orderId = (String) command.get("orderId");
        String shippingId = shippingService.getShippingId(orderId);

        if (shippingId != null) {
            String status = shippingService.getShipmentStatus(shippingId);
            LocalDateTime estimatedDelivery = shippingService.getEstimatedDeliveryDate(shippingId);
            String trackingNumber = shippingService.getTrackingNumber(shippingId);
            String currentLocation = shippingService.getCurrentLocation(shippingId);

            reply.put("status", "STATUS_UPDATE");
            reply.put("shippingStatus", status);
            reply.put("shippingId", shippingId);
            reply.put("estimatedDelivery", estimatedDelivery);
            reply.put("trackingNumber", trackingNumber);
            reply.put("currentLocation", currentLocation);
            reply.put("timestamp", LocalDateTime.now());

            // If shipment is completed or failed, send appropriate event
            if ("DELIVERED".equals(status)) {
                reply.put("status", "COMPLETED");
            } else if ("FAILED".equals(status)) {
                reply.put("status", "FAILED");
                reply.put("errorMessage", "Shipment failed: " + shippingService.getFailureReason(shippingId));
            }
        } else {
            reply.put("status", "FAILED");
            reply.put("errorMessage", "Shipment not found");
        }
    }

    private void sendErrorReply(String sagaId, String orderId, String action, String errorMessage) {
        Map<String, Object> errorReply = new HashMap<>();
        errorReply.put("sagaId", sagaId);
        errorReply.put("orderId", orderId);
        errorReply.put("service", "shipping");
        errorReply.put("status", "FAILED");
        errorReply.put("errorMessage", errorMessage);

        rabbitTemplate.convertAndSend(
            RabbitConfig.SAGA_REPLY_EXCHANGE,
            "saga.reply." + action.toLowerCase(),
            errorReply
        );
    }
} 