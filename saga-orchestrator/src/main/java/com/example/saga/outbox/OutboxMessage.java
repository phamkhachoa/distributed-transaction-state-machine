package com.example.saga.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(nullable = false)
    private String exchange;

    @Column(nullable = false)
    private String routingKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxMessageStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;
} 