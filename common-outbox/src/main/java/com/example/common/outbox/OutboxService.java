package com.example.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveMessage(OutboxMessage message) {
        outboxMessageRepository.save(message);
    }
} 