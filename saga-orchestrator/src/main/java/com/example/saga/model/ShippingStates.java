package com.example.saga.model;

/**
 * States for the shipping saga
 */
public enum ShippingStates {
    // Initial and final states
    SHIPPING_STARTED,
    SHIPPING_CREATED,
    SHIPPING_COMPLETED,
    SHIPPING_CANCELLED,
    SHIPPING_FAILED,
    
    // Package preparation states
    PACKAGE_CREATING,
    PACKAGE_CREATED,
    PACKAGE_READY,
    
    // Seller states
    SELLER_PREPARING,
    SELLER_PREPARED,
    
    // Transport states
    WAITING_FOR_PICKUP,
    PICKUP_SCHEDULED,
    PICKED_UP,
    IN_TRANSIT,
    ARRIVED_AT_DESTINATION,
    
    // Sorting center states
    SORTING_CENTER_RECEIVED,
    SORTING_CENTER_PROCESSING,
    SORTING_CENTER_COMPLETED,
    
    // Local warehouse states
    LOCAL_WAREHOUSE_RECEIVED,
    LOCAL_WAREHOUSE_PROCESSING,
    LOCAL_WAREHOUSE_READY,
    
    // Delivery states
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
    
    // Return states
    RETURN_INITIATED,
    RETURN_IN_TRANSIT,
    RETURN_COMPLETED,
    RETURNING_TO_SELLER,
    RETURNED_TO_SELLER
} 