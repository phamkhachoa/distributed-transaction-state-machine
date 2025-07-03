package com.example.orderservice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderRequest {
    private Integer customerId;
    private Integer productId;
    private BigDecimal price;
} 