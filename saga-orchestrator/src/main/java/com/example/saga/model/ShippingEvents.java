package com.example.saga.model;

/**
 * Events for the shipping saga
 */
public enum ShippingEvents {
    // Main flow events
    START_SHIPPING,
    PACKAGE_CREATED,
    READY_FOR_PICKUP,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    COMPLETE_SHIPPING,
    
    // Failure events
    DELIVERY_ATTEMPT_FAILED,
    PACKAGE_LOST,
    PACKAGE_DAMAGED,
    
    // Cancellation events
    CANCEL_SHIPPING,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURN_COMPLETED,
    
    // Seller events
    SELLER_STARTED_PREPARING,
    SELLER_COMPLETED_PREPARING,
    SELLER_PREPARED,
    
    // Pickup events
    PICKUP_REQUESTED,
    PICKUP_SCHEDULED,
    PICKUP_COMPLETED,
    
    // Sorting center events
    ARRIVED_AT_SORTING_CENTER,
    SORTING_RECEIVED,
    START_SORTING,
    SORTING_COMPLETED,
    
    // Local warehouse events
    ARRIVED_AT_LOCAL_WAREHOUSE,
    LOCAL_RECEIVED,
    START_LOCAL_PROCESSING,
    LOCAL_READY,
    READY_FOR_DELIVERY,
    
    // Delivery events
    START_DELIVERY,
    DELIVERY_COMPLETED,
    
    // Return events
    START_RETURN,
    
    // Status update events
    LOCATION_UPDATED,
    STATUS_UPDATED,
    UPDATE_LOCATION,
    UPDATE_STATUS,
    
    // Timeout events
    SELLER_PREPARATION_TIMEOUT,
    SELLER_TIMEOUT,
    PICKUP_TIMEOUT,
    SORTING_TIMEOUT,
    LOCAL_PROCESSING_TIMEOUT,
    DELIVERY_TIMEOUT
} 