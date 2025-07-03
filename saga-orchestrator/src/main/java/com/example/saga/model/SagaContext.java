package com.example.saga.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Context object that holds all data related to a saga execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaContext implements Serializable {
    private String sagaId;
    private Map<String, Object> payload;
    private String lastError;
} 