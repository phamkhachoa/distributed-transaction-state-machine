package com.example.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaCommand {
    private String sagaId;
    private String requestId;  // ID duy nhất cho mỗi request
    private String action;     // Hành động cụ thể
    private Map<String, Object> payload;
    private long timestamp;    // Thời gian tạo request
} 