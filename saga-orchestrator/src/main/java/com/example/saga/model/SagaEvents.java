package com.example.saga.model;

/**
 * Events that can be triggered in a saga state machine
 */
public enum SagaEvents {
    // Lifecycle events
    START_SAGA,
    SAGA_COMPLETED,
    SAGA_FAILED,
    SAGA_CANCELLED,
    COMPENSATE,
    RESUME,
    
    // Payment events
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PAYMENT_TIMEOUT,
    PAYMENT_COMPENSATED,
    
    // Inventory events
    START_INVENTORY,
    INVENTORY_RESERVED,
    INVENTORY_INSUFFICIENT,
    INVENTORY_FAILED,
    INVENTORY_TIMEOUT,
    INVENTORY_COMPENSATED,
    
    // Shipping events
    START_SHIPPING,
    SHIPPING_SCHEDULED,
    SHIPPING_FAILED,
    SHIPPING_TIMEOUT,
    SHIPPING_COMPENSATED,
    
    // Completion events
    COMPLETE_SAGA
} 