package com.example.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRelay {

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;

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
                rabbitTemplate.convertAndSend(
                    message.getExchange(),
                    message.getRoutingKey(),
                    message.getPayload()
                );
                message.setStatus(OutboxMessageStatus.SENT);
                log.debug("Relayed message {} successfully.", message.getId());
            } catch (Exception e) {
                log.error("Failed to relay message {}. Error: {}", message.getId(), e.getMessage());
                message.setStatus(OutboxMessageStatus.FAILED);
            }
            outboxMessageRepository.save(message);
        }
    }
} 