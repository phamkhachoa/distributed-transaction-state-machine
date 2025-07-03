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
public class SagaReply implements Serializable {
    private String sagaId;
    private String stateId;
    private boolean success;
    private Map<String, Object> output;
    private String failureReason;
} 