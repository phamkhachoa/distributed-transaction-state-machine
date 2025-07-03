package com.example.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaReply implements Serializable {
    private String sagaId;
    private String stateId;
    private boolean success;
    private Map<String, Object> output;
    private String failureReason;
} 