package com.example.common.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM OutboxMessage m WHERE m.status = 'PENDING' ORDER BY m.createdAt ASC")
    List<OutboxMessage> findPendingMessagesWithLock();

} 