package com.example.saga.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class SagaCheckpoint {
    
    @Column(name = "state", nullable = false)
    private String state;
    
    @Column(name = "event")
    private String event;
    
    @Column(name = "action_result", columnDefinition = "TEXT")
    private String actionResult;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "retry_count")
    private Integer retryCount;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    public void markRetry(String error) {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        this.errorMessage = error;
    }
} 