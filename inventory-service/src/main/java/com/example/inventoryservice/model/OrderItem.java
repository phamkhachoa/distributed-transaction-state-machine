package com.example.inventoryservice.model;

import lombok.Data;

@Data
public class OrderItem {
    private String productId;
    private int quantity;
} 