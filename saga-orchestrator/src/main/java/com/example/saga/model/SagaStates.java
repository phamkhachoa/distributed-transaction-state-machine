package com.example.saga.model;

/**
 * States of a saga state machine
 */
public enum SagaStates {
    // Initial and final states
    SAGA_STARTED,
    ORDER_CREATED,
    ORDER_COMPLETED,
    SAGA_FAILED,
    ORDER_CANCELLED,
    
    // Order states
    ORDER_VALIDATING,
    ORDER_VALIDATED,
    ORDER_VALIDATION_FAILED,
    
    // Payment states
    PAYMENT_PENDING,
    PAYMENT_PROCESSING,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    
    // Inventory states
    INVENTORY_RESERVING,
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    
    // Shipping states
    SHIPPING_SCHEDULING,
    SHIPPING_SCHEDULED,
    SHIPPING_FAILED,
    
    // Compensation states
    COMPENSATING_PAYMENT,
    COMPENSATING_INVENTORY,
    COMPENSATING_SHIPPING,
    COMPENSATION_COMPLETED
} 