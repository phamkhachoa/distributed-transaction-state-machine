package com.example.common.outbox;

public enum OutboxMessageStatus {
    PENDING,
    SENT,
    FAILED,
    DUPLICATE
} 