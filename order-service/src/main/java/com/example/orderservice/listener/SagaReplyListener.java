package com.example.orderservice.listener;

import com.example.orderservice.service.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaReplyListener {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "order-status-update")
    public void onOrderStatusUpdate(String message) {
        try {
            log.info("Received order status update: {}", message);
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});

            Long orderId = Long.parseLong(payload.get("orderId").toString());
            String status = (String) payload.get("status");

            orderService.updateOrderStatus(orderId, status);
            log.info("Updated order {} status to {}", orderId, status);

        } catch (Exception e) {
            log.error("Error processing order status update", e);
        }
    }
} 