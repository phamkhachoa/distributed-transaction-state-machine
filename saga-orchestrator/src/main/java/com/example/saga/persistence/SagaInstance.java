package com.example.saga.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA Entity for persisting saga instance state
 */
@Entity
@Table(name = "saga_instance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaInstance {
    
    @Id
    @Column(name = "id")
    private String id;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @Column(name = "saga_type", nullable = false)
    private String sagaType;
    
    @Column(name = "current_state", nullable = false)
    private String currentState;
    
    @Column(name = "saga_data", columnDefinition = "TEXT")
    private String sagaData;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @ElementCollection
    @CollectionTable(name = "saga_instance_metadata", 
            joinColumns = @JoinColumn(name = "saga_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata = new HashMap<>();
    
    @ElementCollection
    @CollectionTable(name = "saga_instance_events", 
            joinColumns = @JoinColumn(name = "saga_id"))
    @OrderColumn(name = "event_order")
    @Column(name = "event")
    private List<String> events = new ArrayList<>();
    
    @Column(name = "last_retry_count")
    private Integer lastRetryCount;
    
    @Column(name = "next_retry_time")
    private LocalDateTime nextRetryTime;
    
    @ElementCollection
    @CollectionTable(
        name = "saga_checkpoints",
        joinColumns = @JoinColumn(name = "saga_id")
    )
    @OrderColumn(name = "checkpoint_order")
    private List<SagaCheckpoint> checkpoints = new ArrayList<>();
    
    @Column(name = "compensation_triggered")
    private boolean compensationTriggered;
    
    @Column(name = "compensation_state")
    private String compensationState;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        version = 0L;
        lastRetryCount = 0;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public void addCheckpoint(String state, String event, String actionResult) {
        SagaCheckpoint checkpoint = SagaCheckpoint.builder()
                .state(state)
                .event(event)
                .actionResult(actionResult)
                .timestamp(LocalDateTime.now())
                .build();
        
        if (checkpoints == null) {
            checkpoints = new ArrayList<>();
        }
        checkpoints.add(checkpoint);
    }
    
    public void incrementRetryCount() {
        if (lastRetryCount == null) {
            lastRetryCount = 0;
        }
        lastRetryCount++;
    }
    
    public void updateNextRetryTime(long delayInSeconds) {
        nextRetryTime = LocalDateTime.now().plusSeconds(delayInSeconds);
    }
    
    public boolean canRetry(int maxRetries) {
        return lastRetryCount < maxRetries;
    }
    
    public boolean isRetryDue() {
        return nextRetryTime == null || LocalDateTime.now().isAfter(nextRetryTime);
    }
    
    public void startCompensation(String fromState) {
        this.compensationTriggered = true;
        this.compensationState = fromState;
        this.status = "COMPENSATING";
    }
    
    /**
     * Add metadata to the saga instance
     */
    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * Get metadata value by key
     */
    public String getMetadata(String key) {
        if (metadata == null) {
            return null;
        }
        return metadata.get(key);
    }
    
    /**
     * Add an event to the saga instance history
     */
    public void addEvent(String event) {
        if (events == null) {
            events = new ArrayList<>();
        }
        events.add(event);
    }
    
    /**
     * Get the order ID associated with this saga
     */
    public String getOrderId() {
        return getMetadata("orderId");
    }
    
    /**
     * Set the order ID for this saga
     */
    public void setOrderId(String orderId) {
        addMetadata("orderId", orderId);
    }
} 