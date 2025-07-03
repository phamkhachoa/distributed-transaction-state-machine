package com.example.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaCommand implements Serializable {
    private String sagaId;
    private String stateId;
    private String type; // e.g., "CHARGE_PAYMENT", "REFUND_PAYMENT"
    private Map<String, Object> payload;
} 