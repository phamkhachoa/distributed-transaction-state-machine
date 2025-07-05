package com.example.saga.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingContext {
    private String sagaId;
    private String orderId;
    private String shippingAddress;
    private String currentLocation;
    private String currentStatus;
    private LocalDateTime startTime;
    private LocalDateTime lastStatusUpdate;
    private String failureReason;
    private String returnReason;
    private LocalDateTime returnStarted;
    private LocalDateTime estimatedDeliveryTime;
    
    @Builder.Default
    private List<ShippingStatusUpdate> statusHistory = new ArrayList<>();
    
    /**
     * Add a status update to the history
     */
    public void addStatusUpdate(String status, String location) {
        ShippingStatusUpdate update = ShippingStatusUpdate.builder()
                .status(status)
                .location(location)
                .timestamp(LocalDateTime.now())
                .build();
        
        statusHistory.add(update);
        this.currentStatus = status;
        this.currentLocation = location;
        this.lastStatusUpdate = update.getTimestamp();
    }
    
    /**
     * Get the latest status update
     */
    public ShippingStatusUpdate getLatestStatusUpdate() {
        if (statusHistory == null || statusHistory.isEmpty()) {
            return null;
        }
        return statusHistory.get(statusHistory.size() - 1);
    }
    
    /**
     * Status update record
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingStatusUpdate {
        private String status;
        private String location;
        private LocalDateTime timestamp;
        private String notes;
    }
} 
