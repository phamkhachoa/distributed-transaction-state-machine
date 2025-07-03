package com.example.order.outbox;

public enum OutboxMessageStatus {
    PENDING,
    SENT,
    FAILED
} 