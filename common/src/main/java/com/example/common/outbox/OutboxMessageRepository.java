package com.example.common.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM OutboxMessage m WHERE m.status = 'PENDING' ORDER BY m.createdAt ASC")
    List<OutboxMessage> findPendingMessagesWithLock();
    
    /**
     * Kiểm tra xem có tin nhắn nào với idempotency key và status đã cho không
     */
    boolean existsByIdempotencyKeyAndStatus(String idempotencyKey, OutboxMessageStatus status);
    
    /**
     * Tìm tin nhắn theo idempotency key
     */
    Optional<OutboxMessage> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Tìm các tin nhắn đang chờ xử lý theo aggregate ID và type
     */
    @Query("SELECT m FROM OutboxMessage m WHERE m.aggregateId = :aggregateId AND m.aggregateType = :aggregateType AND m.status = 'PENDING'")
    List<OutboxMessage> findPendingByAggregateIdAndType(@Param("aggregateId") String aggregateId, @Param("aggregateType") String aggregateType);
} 