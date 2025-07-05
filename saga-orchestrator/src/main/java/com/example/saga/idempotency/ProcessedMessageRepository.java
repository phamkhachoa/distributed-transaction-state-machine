package com.example.saga.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository để truy vấn các message đã xử lý
 */
@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
    
    /**
     * Kiểm tra xem một request đã được xử lý chưa
     */
    boolean existsByRequestId(String requestId);
    
    /**
     * Tìm message đã xử lý theo requestId
     */
    Optional<ProcessedMessage> findByRequestId(String requestId);
    
    /**
     * Kiểm tra xem một saga và request đã được xử lý chưa
     */
    boolean existsBySagaIdAndRequestId(String sagaId, String requestId);
    
    /**
     * Tìm message đã xử lý theo sagaId và operation
     */
    Optional<ProcessedMessage> findBySagaIdAndOperation(String sagaId, String operation);
} 