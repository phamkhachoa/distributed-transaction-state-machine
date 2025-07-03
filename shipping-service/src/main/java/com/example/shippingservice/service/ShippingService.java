package com.example.shippingservice.service;

import com.example.shippingservice.model.Shipping;
import com.example.shippingservice.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final ShippingRepository shippingRepository;

    public Shipping scheduleShipment(String orderId) {
        Shipping shipping = Shipping.builder()
                .orderId(orderId)
                .status("SCHEDULED")
                .estimatedDeliveryDate(LocalDate.now().plusDays(5))
                .trackingNumber(UUID.randomUUID().toString())
                .build();
        return shippingRepository.save(shipping);
    }

    public void cancelShipment(Long shippingId) {
        Shipping shipping = shippingRepository.findById(shippingId)
                .orElseThrow(() -> new RuntimeException("Shipment not found"));

        if ("DELIVERED".equals(shipping.getStatus())) {
            throw new IllegalStateException("Cannot cancel a delivered shipment.");
        }

        shipping.setStatus("CANCELLED");
        shippingRepository.save(shipping);
    }
} 