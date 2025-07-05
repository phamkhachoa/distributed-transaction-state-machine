package com.example.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;

    /**
     * Lưu tin nhắn vào outbox với kiểm tra idempotency
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMessage(OutboxMessage message) {
        // Nếu có idempotency key, kiểm tra xem tin nhắn đã tồn tại chưa
        if (message.getIdempotencyKey() != null) {
            Optional<OutboxMessage> existingMessage = 
                    outboxMessageRepository.findByIdempotencyKey(message.getIdempotencyKey());
            
            if (existingMessage.isPresent()) {
                log.info("Duplicate message detected with idempotency key: {}", 
                        message.getIdempotencyKey());
                
                // Nếu tin nhắn cũ đã được gửi thành công, đánh dấu tin nhắn mới là DUPLICATE
                if (existingMessage.get().getStatus() == OutboxMessageStatus.SENT) {
                    message.setStatus(OutboxMessageStatus.DUPLICATE);
                    message.setOriginalMessageId(existingMessage.get().getId());
                    outboxMessageRepository.save(message);
                    return;
                }
                
                // Nếu tin nhắn cũ bị lỗi, tăng retry count và lưu lại
                if (existingMessage.get().getStatus() == OutboxMessageStatus.FAILED) {
                    OutboxMessage existingMsg = existingMessage.get();
                    existingMsg.setRetryCount(existingMsg.getRetryCount() + 1);
                    existingMsg.setStatus(OutboxMessageStatus.PENDING);
                    existingMsg.setPayload(message.getPayload()); // Cập nhật payload mới nếu cần
                    outboxMessageRepository.save(existingMsg);
                    return;
                }
            }
        }
        
        // Nếu không có idempotency key hoặc tin nhắn chưa tồn tại, lưu tin nhắn mới
        outboxMessageRepository.save(message);
    }
    
    /**
     * Đánh dấu tin nhắn đã được xử lý
     */
    @Transactional
    public void markAsProcessed(UUID messageId) {
        outboxMessageRepository.findById(messageId).ifPresent(message -> {
            message.setStatus(OutboxMessageStatus.SENT);
            message.setProcessedAt(LocalDateTime.now());
            outboxMessageRepository.save(message);
        });
    }
    
    /**
     * Đánh dấu tin nhắn bị lỗi
     */
    @Transactional
    public void markAsFailed(UUID messageId) {
        outboxMessageRepository.findById(messageId).ifPresent(message -> {
            message.setStatus(OutboxMessageStatus.FAILED);
            message.setRetryCount(message.getRetryCount() + 1);
            outboxMessageRepository.save(message);
        });
    }
} 