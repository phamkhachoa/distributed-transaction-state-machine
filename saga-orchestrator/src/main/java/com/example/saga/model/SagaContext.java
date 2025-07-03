package com.example.saga.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Context object that holds all data related to a saga execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaContext {
    private String sagaId;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String paymentId;
    private String inventoryReservationId;
    private String shippingId;
    private String shippingSagaId;
    private String shippingAddress;
    
    // Product information
    private Map<String, Integer> products = new HashMap<>();
    
    // Timestamps
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // Status and error information
    private String status;
    private String errorMessage;
    private String failedStep;
    
    // Additional metadata
    private Map<String, Object> metadata = new HashMap<>();
    
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }
} 