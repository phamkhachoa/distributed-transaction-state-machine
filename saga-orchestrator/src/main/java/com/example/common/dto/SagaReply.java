package com.example.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaReply {
    private String sagaId;
    private String requestId;  // ID duy nhất cho mỗi request
    private boolean success;
    private String reason;     // Lý do lỗi nếu có
    private Map<String, Object> payload;
    private long timestamp;    // Thời gian tạo reply
} 