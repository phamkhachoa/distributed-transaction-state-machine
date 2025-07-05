package com.example.saga.idempotency;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity để lưu trữ các message đã được xử lý để đảm bảo idempotency
 */
@Entity
@Table(name = "processed_messages", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"request_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "request_id", nullable = false)
    private String requestId;
    
    @Column(name = "saga_id")
    private String sagaId;
    
    @Column(name = "service_name", nullable = false)
    private String serviceName;
    
    @Column(name = "operation", nullable = false)
    private String operation;
    
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
    
    @Column(name = "result_id")
    private String resultId;  // ID của kết quả (nếu có)
    
    @Column(name = "status")
    private String status;
    
    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }
} 