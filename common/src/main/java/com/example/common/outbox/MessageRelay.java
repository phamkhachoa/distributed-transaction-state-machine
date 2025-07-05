package com.example.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRelay {

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;
    
    @Value("${outbox.relay.max-retries:3}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${outbox.relay.delay:10000}")
    @Transactional
    public void relayMessages() {
        log.trace("Checking for pending messages in outbox...");
        List<OutboxMessage> messages = outboxMessageRepository.findPendingMessagesWithLock();
        
        if (messages.isEmpty()) {
            return;
        }

        log.info("Found {} pending messages. Relaying...", messages.size());
        for (OutboxMessage message : messages) {
            try {
                // Kiểm tra xem tin nhắn đã được gửi thành công trước đó
                if (message.getIdempotencyKey() != null && 
                    outboxMessageRepository.existsByIdempotencyKeyAndStatus(
                        message.getIdempotencyKey(), OutboxMessageStatus.SENT)) {
                    
                    // Đánh dấu tin nhắn này là DUPLICATE và bỏ qua
                    message.setStatus(OutboxMessageStatus.DUPLICATE);
                    outboxMessageRepository.save(message);
                    log.debug("Marked message {} as DUPLICATE.", message.getId());
                    continue;
                }
                
                // Gửi tin nhắn
                rabbitTemplate.convertAndSend(
                    message.getExchange(),
                    message.getRoutingKey(),
                    message.getPayload()
                );
                
                // Cập nhật trạng thái
                message.setStatus(OutboxMessageStatus.SENT);
                message.setProcessedAt(LocalDateTime.now());
                log.debug("Relayed message {} successfully.", message.getId());
            } catch (Exception e) {
                // Xử lý lỗi
                log.error("Failed to relay message {}. Error: {}", message.getId(), e.getMessage());
                message.setRetryCount(message.getRetryCount() + 1);
                
                // Nếu vượt quá số lần retry tối đa, đánh dấu là FAILED
                if (message.getRetryCount() >= maxRetries) {
                    message.setStatus(OutboxMessageStatus.FAILED);
                    log.warn("Message {} has exceeded maximum retry count.", message.getId());
                }
            }
            outboxMessageRepository.save(message);
        }
    }
} 