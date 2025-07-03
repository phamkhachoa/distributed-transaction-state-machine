package com.example.saga.model;

/**
 * Events that can be triggered in a saga state machine
 */
public enum SagaEvents {
    // Order creation events
    START_SAGA,
    VALIDATE_ORDER,
    ORDER_VALIDATED,
    ORDER_VALIDATION_FAILED,
    
    // Payment events
    PROCESS_PAYMENT,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    PAYMENT_SUCCESS,
    PAYMENT_TIMEOUT,
    
    // Inventory events
    RESERVE_INVENTORY,
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    INVENTORY_INSUFFICIENT,
    INVENTORY_TIMEOUT,
    START_INVENTORY,
    
    // Shipping events
    START_SHIPPING,
    SHIPPING_SCHEDULED,
    SHIPPING_FAILED,
    SHIPPING_TIMEOUT,
    
    // Compensation events
    COMPENSATE_PAYMENT,
    COMPENSATE_INVENTORY,
    COMPENSATE_SHIPPING,
    PAYMENT_COMPENSATED,
    INVENTORY_COMPENSATED,
    SHIPPING_COMPENSATED,
    
    // Completion events
    COMPLETE_SAGA,
    SAGA_COMPLETED,
    SAGA_FAILED,
    
    // Timeout events
    TIMEOUT_SAGA
} 