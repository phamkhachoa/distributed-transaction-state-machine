package com.example.saga.dto;

import com.example.saga.model.SagaStates;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class SagaResponse {
    private String sagaId;
    private String orderId;
    private SagaStates currentState;
    private String status;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;
    private Map<String, Object> metadata;
    
    // Additional fields for shipping status
    private String shippingId;
    private String lastShippingStatus;
    private LocalDateTime lastShippingUpdate;
} 