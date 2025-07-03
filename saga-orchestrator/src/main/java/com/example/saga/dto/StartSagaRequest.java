package com.example.saga.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class StartSagaRequest {
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private Map<String, Integer> products;
    private Map<String, Object> metadata;
} 