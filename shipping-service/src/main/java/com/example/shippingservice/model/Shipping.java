package com.example.shippingservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipping {
    @Id
    @GeneratedValue
    private Long id;
    private String orderId;
    private String status; // e.g., "SCHEDULED", "SHIPPED", "DELIVERED", "CANCELLED"
    private LocalDate estimatedDeliveryDate;
    private String trackingNumber;
} 