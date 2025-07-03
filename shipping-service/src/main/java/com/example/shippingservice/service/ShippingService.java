package com.example.shippingservice.service;

import com.example.common.dto.SagaCommand;
import com.example.shippingservice.entity.Shipping;
import com.example.shippingservice.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingRepository shippingRepository;

    @Transactional
    public Shipping scheduleShipping(SagaCommand command) {
        Map<String, Object> payload = command.getPayload();
        Integer orderId = (Integer) payload.get("id");
        log.info("Scheduling shipping for order: {}", orderId);
        Shipping shipping = Shipping.builder()
                .orderId(orderId)
                .status("SCHEDULED")
                .build();
        Shipping savedShipping = shippingRepository.save(shipping);
        log.info("Shipping scheduled with id: {} for order: {}", savedShipping.getId(), orderId);
        return savedShipping;
    }

    @Transactional
    public void cancelShipping(SagaCommand command) {
        Map<String, Object> payload = command.getPayload();
        Integer shippingId = (Integer) payload.get("shippingId");
        Shipping shipping = shippingRepository.findById(shippingId)
                .orElseThrow(() -> new RuntimeException("Shipping not found for id: " + shippingId));
        shipping.setStatus("CANCELLED");
        shippingRepository.save(shipping);
        log.info("Shipping cancelled for id: {}", shippingId);
    }
} 