package com.example.saga.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Context for saga execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaContext implements Serializable {
    private String sagaId;
    private String orderId;
    private String userId;
    private Double amount;
    private Map<String, Integer> products;
    
    // Payload chứa dữ liệu chung
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();
    
    // ID tham chiếu đến các service bên ngoài
    private String paymentId;           // ID của payment
    private String reservationId;       // ID của inventory reservation
    private String shippingId;          // ID của shipping
    private String notificationId;      // ID của notification
    
    // ID của các request để đảm bảo idempotency
    private String paymentRequestId;    // ID của payment request
    private String refundRequestId;     // ID của refund request
    private String reserveRequestId;    // ID của inventory reserve request
    private String releaseRequestId;    // ID của inventory release request
    private String shippingRequestId;   // ID của shipping request
    private String cancelShippingRequestId; // ID của cancel shipping request
    
    // Metadata
    private LocalDateTime startTime;
    private String status;
    private String lastError;
    
    /**
     * Lấy ID tham chiếu đến payment service
     */
    public String getPaymentId() {
        return paymentId;
    }
    
    /**
     * Đặt ID tham chiếu đến payment service
     */
    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
        if (payload == null) {
            payload = new HashMap<>();
        }
        payload.put("paymentId", paymentId);
    }
    
    /**
     * Lấy ID tham chiếu đến inventory service
     */
    public String getReservationId() {
        return reservationId;
    }
    
    /**
     * Đặt ID tham chiếu đến inventory service
     */
    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
        if (payload == null) {
            payload = new HashMap<>();
        }
        payload.put("reservationId", reservationId);
    }
    
    /**
     * Lấy ID tham chiếu đến shipping service
     */
    public String getShippingId() {
        return shippingId;
    }
    
    /**
     * Đặt ID tham chiếu đến shipping service
     */
    public void setShippingId(String shippingId) {
        this.shippingId = shippingId;
        if (payload == null) {
            payload = new HashMap<>();
        }
        payload.put("shippingId", shippingId);
    }
    
    /**
     * Đặt thời gian bắt đầu
     */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        if (payload == null) {
            payload = new HashMap<>();
        }
        payload.put("startTime", startTime);
    }
    
    /**
     * Lấy ID của saga
     */
    public String getSagaId() {
        return sagaId;
    }
    
    /**
     * Đặt ID của saga
     */
    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
        if (payload == null) {
            payload = new HashMap<>();
        }
        payload.put("sagaId", sagaId);
    }
    
    /**
     * Lấy payload
     */
    public Map<String, Object> getPayload() {
        if (payload == null) {
            payload = new HashMap<>();
        }
        return payload;
    }
} 