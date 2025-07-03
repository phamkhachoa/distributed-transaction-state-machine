package com.example.shippingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaCommand implements Serializable {
    private String sagaId;
    private String service;
    private String action;
    private Map<String, Object> payload;
} 