package com.example.paymentservice.dto;

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
    private String type;
    private Map<String, Object> payload;
} 