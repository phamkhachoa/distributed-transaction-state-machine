package com.example.saga.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "saga_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "saga_id", nullable = false)
    private String sagaId;
    
    @Column(name = "saga_type", nullable = false)
    private String sagaType;
    
    @Column(name = "source_state", nullable = false)
    private String sourceState;
    
    @Column(name = "target_state", nullable = false)
    private String targetState;
    
    @Column(name = "event", nullable = false)
    private String event;
    
    @Column(name = "action_name")
    private String actionName;
    
    @Column(name = "action_result", columnDefinition = "TEXT")
    private String actionResult;
    
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "instance_id", nullable = false)
    private String instanceId;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "is_compensation", nullable = false)
    private boolean isCompensation;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
} 