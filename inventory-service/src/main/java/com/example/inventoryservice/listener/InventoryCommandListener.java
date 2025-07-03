package com.example.inventoryservice.listener;

import com.example.inventoryservice.config.RabbitConfig;
import com.example.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryCommandListener {

    private final InventoryService inventoryService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.INVENTORY_COMMAND_QUEUE)
    public void handleCommand(Map<String, Object> command) {
        String sagaId = (String) command.get("sagaId");
        String orderId = (String) command.get("orderId");
        String action = (String) command.get("action");

        log.info("Received inventory command: {} for saga: {}, order: {}", action, sagaId, orderId);

        try {
            Map<String, Object> reply = new HashMap<>();
            reply.put("sagaId", sagaId);
            reply.put("orderId", orderId);
            reply.put("service", "inventory");

            switch (action.toUpperCase()) {
                case "RESERVE":
                    handleReserve(command, reply);
                    break;
                    
                case "RELEASE":
                    handleRelease(command, reply);
                    break;
                    
                case "HEARTBEAT":
                    handleHeartbeat(command, reply);
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
            log.error("Error processing inventory command", e);
            sendErrorReply(sagaId, orderId, action, e.getMessage());
        }
    }

    private void handleReserve(Map<String, Object> command, Map<String, Object> reply) {
        String orderId = (String) command.get("orderId");
        @SuppressWarnings("unchecked")
        Map<String, Integer> products = (Map<String, Integer>) command.get("products");

        String reservationId = inventoryService.reserveInventory(orderId, products);

        if (reservationId != null) {
            reply.put("status", "RESERVED");
            reply.put("reservationId", reservationId);
        } else {
            reply.put("status", "INSUFFICIENT");
            reply.put("errorMessage", "Insufficient inventory for one or more products");
        }
    }

    private void handleRelease(Map<String, Object> command, Map<String, Object> reply) {
        String orderId = (String) command.get("orderId");
        String reservationId = (String) command.get("inventoryReservationId");
        @SuppressWarnings("unchecked")
        Map<String, Integer> products = (Map<String, Integer>) command.get("products");

        boolean released = inventoryService.releaseInventory(orderId, reservationId, products);

        if (released) {
            reply.put("status", "RELEASED");
        } else {
            reply.put("status", "FAILED");
            reply.put("errorMessage", "Failed to release inventory");
        }
    }

    private void handleHeartbeat(Map<String, Object> command, Map<String, Object> reply) {
        String orderId = (String) command.get("orderId");
        String reservationId = inventoryService.getReservationId(orderId);

        if (reservationId != null) {
            reply.put("status", "HEARTBEAT");
            reply.put("reservationId", reservationId);
            reply.put("isValid", inventoryService.isReservationValid(reservationId));
        } else {
            reply.put("status", "FAILED");
            reply.put("errorMessage", "Reservation not found");
        }
    }

    private void sendErrorReply(String sagaId, String orderId, String action, String errorMessage) {
        Map<String, Object> errorReply = new HashMap<>();
        errorReply.put("sagaId", sagaId);
        errorReply.put("orderId", orderId);
        errorReply.put("service", "inventory");
        errorReply.put("status", "FAILED");
        errorReply.put("errorMessage", errorMessage);

        rabbitTemplate.convertAndSend(
            RabbitConfig.SAGA_REPLY_EXCHANGE,
            "saga.reply." + action.toLowerCase(),
            errorReply
        );
    }
} 