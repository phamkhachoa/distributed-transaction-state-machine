package com.example.orderservice.service;

import com.example.common.outbox.OutboxMessage;
import com.example.common.outbox.OutboxService;
import com.example.orderservice.dto.OrderRequest;
import com.example.common.dto.SagaCommand;
import com.example.orderservice.entity.Order;
import com.example.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;


    @Transactional
    public Order createOrder(OrderRequest orderRequest) {
        Order order = Order.builder()
                .productId(orderRequest.getProductId())
                .price(orderRequest.getPrice())
                .orderStatus("CREATED")
                .customerId(orderRequest.getCustomerId())
                .build();
        order = orderRepository.save(order);

        SagaCommand command = SagaCommand.builder()
                .sagaId(UUID.randomUUID())
                .payload(objectMapper.convertValue(order, Map.class))
                .build();

        try {
            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .exchange("saga-commands")
                    .routingKey("order.created")
                    .payload(objectMapper.writeValueAsString(command))
                    .build();
            outboxService.saveMessage(outboxMessage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing saga command", e);
        }


        return order;
    }
} 