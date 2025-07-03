package com.example.shippingservice.service;

import com.example.shippingservice.model.Shipping;
import com.example.shippingservice.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingRepository shippingRepository;

    @Transactional
    public Shipping scheduleShipping(String orderId, String customerId) {
        // Simulate failure if customerId is "FAIL"
        if ("FAIL".equalsIgnoreCase(customerId)) {
            throw new RuntimeException("Shipping scheduling failed for customer: " + customerId);
        }
        
        Shipping shipping = Shipping.builder()
                .orderId(orderId)
                .customerId(customerId)
                .status("SCHEDULED")
                .build();
        return shippingRepository.save(shipping);
    }

    @Transactional
    public Optional<Shipping> cancelShipping(String orderId) {
        return shippingRepository.findByOrderId(orderId)
                .map(shipping -> {
                    shipping.setStatus("CANCELLED");
                    return shippingRepository.save(shipping);
                });
    }
} 