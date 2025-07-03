package com.example.shippingservice.listener;

import com.example.common.dto.SagaCommand;
import com.example.common.dto.SagaReply;
import com.example.common.outbox.OutboxMessage;
import com.example.common.outbox.OutboxService;
import com.example.shippingservice.entity.Shipping;
import com.example.shippingservice.service.ShippingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingRequestListener {

    private final ShippingService shippingService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "shipping-requests")
    public void handleRequest(SagaCommand command) {
        String action = command.getPayload().get("action").toString();
        SagaReply reply;
        try {
            if ("SCHEDULE_SHIPPING".equals(action)) {
                Shipping shipping = shippingService.scheduleShipping(command);
                Map<String, Object> payload = command.getPayload();
                payload.put("shippingId", shipping.getId());
                reply = createSuccessReply(command, payload);
                saveReplyToOutbox("shipping.succeeded", reply);
            } else if ("COMPENSATE_SHIPPING".equals(action)) {
                shippingService.cancelShipping(command);
                reply = createSuccessReply(command, command.getPayload());
                saveReplyToOutbox("shipping.compensated", reply);
            } else {
                throw new IllegalArgumentException("Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Failed to process request for sagaId: {}", command.getSagaId(), e);
            reply = createFailureReply(command, e.getMessage());
            saveReplyToOutbox("shipping.failed", reply);
        }
    }

    private void saveReplyToOutbox(String routingKey, SagaReply reply) {
        try {
            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .exchange("saga-replies")
                    .routingKey(routingKey)
                    .payload(objectMapper.writeValueAsString(reply))
                    .build();
            outboxService.saveMessage(outboxMessage);
        } catch (JsonProcessingException e) {
            log.error("Error serializing saga reply for sagaId: {}", reply.getSagaId(), e);
        }
    }

    private SagaReply createSuccessReply(SagaCommand command, Map<String, Object> payload) {
        return SagaReply.builder()
                .sagaId(command.getSagaId())
                .success(true)
                .payload(payload)
                .build();
    }

    private SagaReply createFailureReply(SagaCommand command, String reason) {
        return SagaReply.builder()
                .sagaId(command.getSagaId())
                .success(false)
                .reason(reason)
                .payload(command.getPayload())
                .build();
    }
} 