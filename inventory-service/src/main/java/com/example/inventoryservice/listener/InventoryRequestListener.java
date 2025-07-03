package com.example.inventoryservice.listener;

import com.example.inventoryservice.dto.SagaReply;
import com.example.inventoryservice.model.OrderItem;
import com.example.inventoryservice.service.InventoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryRequestListener {

    private final InventoryService inventoryService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private static final String SAGA_REPLY_QUEUE = "saga-replies";

    @RabbitListener(queues = "inventory-requests")
    public void handleInventoryRequest(String message) {
        SagaReply reply;
        String sagaId = null;
        try {
            Map<String, Object> command = objectMapper.readValue(message, new TypeReference<>() {});
            sagaId = (String) command.get("sagaId");
            String action = (String) command.get("action");
            Map<String, Object> payload = (Map<String, Object>) command.get("payload");
            List<OrderItem> items = objectMapper.convertValue(payload.get("items"), new TypeReference<>() {});

            log.info("Received inventory request for sagaId: {}, action: {}", sagaId, action);

            if ("RESERVE_INVENTORY".equals(action)) {
                String reservationId = inventoryService.reserveInventory(items);
                payload.put("reservationId", reservationId);
                reply = SagaReply.builder()
                        .sagaId(sagaId)
                        .service("inventory")
                        .status("SUCCESS")
                        .payload(payload)
                        .build();

            } else if ("RELEASE_INVENTORY".equals(action)) {
                String reservationId = (String) payload.get("reservationId");
                inventoryService.releaseInventory(reservationId);
                reply = SagaReply.builder()
                        .sagaId(sagaId)
                        .service("inventory")
                        .status("SUCCESS")
                        .payload(payload)
                        .build();
            } else {
                throw new IllegalArgumentException("Unknown action: " + action);
            }

        } catch (Exception e) {
            log.error("Error processing inventory request for sagaId: {}", sagaId, e);
            reply = SagaReply.builder()
                    .sagaId(sagaId)
                    .service("inventory")
                    .status("FAILED")
                    .failureReason(e.getMessage())
                    .build();
        }

        try {
            String replyMessage = objectMapper.writeValueAsString(reply);
            rabbitTemplate.convertAndSend(SAGA_REPLY_QUEUE, replyMessage);
            log.info("Sent inventory reply for sagaId: {}", sagaId);
        } catch (Exception e) {
            log.error("Error sending reply for sagaId: {}", sagaId, e);
        }
    }
} 