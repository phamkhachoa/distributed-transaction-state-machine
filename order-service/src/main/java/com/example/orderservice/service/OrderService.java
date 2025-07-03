package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import io.seata.saga.engine.StateMachineEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final StateMachineEngine stateMachineEngine;

    @Transactional
    public Order createOrder(Order order) {
        // 1. Save the initial order
        order.setStatus("PENDING");
        orderRepository.save(order);

        // 2. Start the Seata Saga state machine
        Map<String, Object> startParams = new HashMap<>();
        startParams.put("orderId", order.getId());
        startParams.put("customerId", order.getCustomerId());
        startParams.put("amount", order.getAmount().toString());
        startParams.put("items", order.getItems());

        stateMachineEngine.start("HybridOrderProcessingSaga", null, startParams);

        return order;
    }

    @Transactional
    public void cancelOrder(Order order) {
        // Start the Seata Saga state machine for cancellation
        Map<String, Object> startParams = new HashMap<>();
        startParams.put("orderId", order.getId());
        startParams.put("customerId", order.getCustomerId());
        startParams.put("amount", order.getAmount().toString());
        startParams.put("items", order.getItems());
        
        Map<String, Object> context = new HashMap<>();
        context.put("startParams", startParams);
        context.put("orderId", order.getId());
        context.put("items", order.getItems());
        context.put("sagaId", order.getId());

        stateMachineEngine.start("OrderCancellationSaga", null, context);
    }

    public boolean isCancellable(String orderId) {
        Optional<Order> orderOpt = getOrder(orderId);
        if (orderOpt.isEmpty()) {
            return false;
        }
        // Cannot cancel if status is already completed, shipped or cancelled
        return !Arrays.asList("COMPLETED", "SHIPPED", "CANCELLED").contains(orderOpt.get().getStatus());
    }

    @Transactional
    public Optional<Order> updateOrderStatus(String orderId, String status) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    order.setStatus(status);
                    return orderRepository.save(order);
                });
    }

    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }
} 