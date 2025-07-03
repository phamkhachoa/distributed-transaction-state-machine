package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import com.example.orderservice.orchestrator.SagaOrchestrator;
import com.example.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;

    @Transactional
    public Order createOrder(Order order) {
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        // Start Saga
        String sagaId = UUID.randomUUID().toString();
        Map<String, Object> payload = objectMapper.convertValue(savedOrder, new TypeReference<>() {});
        payload.put("sagaId", sagaId);

        sagaOrchestrator.startSaga(sagaId, payload);

        return savedOrder;
    }

    @Transactional
    public void updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);
    }
} 