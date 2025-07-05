package com.example.saga.service;

import com.example.saga.model.SagaContext;
import com.example.saga.persistence.SagaInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service để kiểm tra trạng thái thực tế của các service bên ngoài khi phục hồi saga
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalStateVerificationService {

    private final RestTemplate restTemplate;
    private final SagaOrchestrationService orchestrationService;
    
    // URL các service bên ngoài
    private static final String PAYMENT_SERVICE_URL = "http://payment-service:8080/api/payments/{id}";
    private static final String INVENTORY_SERVICE_URL = "http://inventory-service:8080/api/inventory/reservations/{id}";
    private static final String SHIPPING_SERVICE_URL = "http://shipping-service:8080/api/shipments/{id}";
    
    /**
     * Kiểm tra trạng thái payment
     */
    public PaymentStatus verifyPaymentStatus(String paymentId) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    PAYMENT_SERVICE_URL, 
                    Map.class, 
                    paymentId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String status = (String) response.getBody().get("status");
                if ("COMPLETED".equals(status)) {
                    return PaymentStatus.COMPLETED;
                } else if ("FAILED".equals(status)) {
                    return PaymentStatus.FAILED;
                } else if ("PROCESSING".equals(status)) {
                    return PaymentStatus.PROCESSING;
                }
            }
            
            return PaymentStatus.UNKNOWN;
        } catch (Exception e) {
            log.error("Failed to verify payment status for ID: {}", paymentId, e);
            return PaymentStatus.UNKNOWN;
        }
    }
    
    /**
     * Kiểm tra trạng thái inventory
     */
    public InventoryStatus verifyInventoryStatus(String reservationId) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    INVENTORY_SERVICE_URL, 
                    Map.class, 
                    reservationId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String status = (String) response.getBody().get("status");
                if ("RESERVED".equals(status)) {
                    return InventoryStatus.RESERVED;
                } else if ("FAILED".equals(status)) {
                    return InventoryStatus.FAILED;
                } else if ("PROCESSING".equals(status)) {
                    return InventoryStatus.PROCESSING;
                }
            }
            
            return InventoryStatus.UNKNOWN;
        } catch (Exception e) {
            log.error("Failed to verify inventory status for ID: {}", reservationId, e);
            return InventoryStatus.UNKNOWN;
        }
    }
    
    /**
     * Kiểm tra trạng thái shipping
     */
    public ShippingStatus verifyShippingStatus(String shippingId) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    SHIPPING_SERVICE_URL, 
                    Map.class, 
                    shippingId);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String status = (String) response.getBody().get("status");
                if ("SCHEDULED".equals(status)) {
                    return ShippingStatus.SCHEDULED;
                } else if ("FAILED".equals(status)) {
                    return ShippingStatus.FAILED;
                } else if ("PROCESSING".equals(status)) {
                    return ShippingStatus.PROCESSING;
                }
            }
            
            return ShippingStatus.UNKNOWN;
        } catch (Exception e) {
            log.error("Failed to verify shipping status for ID: {}", shippingId, e);
            return ShippingStatus.UNKNOWN;
        }
    }
    
    /**
     * Quyết định xem có cần compensation hay không dựa trên trạng thái hiện tại của saga
     */
    public boolean needsCompensation(SagaInstance saga) {
        try {
            String currentState = saga.getCurrentState();
            SagaContext context = (SagaContext) orchestrationService.getSagaContext(saga.getId());
            
            if ("PAYMENT_PROCESSING".equals(currentState)) {
                String paymentId = context.getPaymentId();
                if (paymentId == null) {
                    return false; // Chưa bắt đầu xử lý payment
                }
                
                PaymentStatus status = verifyPaymentStatus(paymentId);
                return status == PaymentStatus.FAILED || status == PaymentStatus.UNKNOWN;
                
            } else if ("INVENTORY_RESERVING".equals(currentState)) {
                String reservationId = context.getReservationId();
                if (reservationId == null) {
                    return false; // Chưa bắt đầu xử lý inventory
                }
                
                InventoryStatus status = verifyInventoryStatus(reservationId);
                return status == InventoryStatus.FAILED || status == InventoryStatus.UNKNOWN;
                
            } else if ("SHIPPING_SCHEDULING".equals(currentState)) {
                String shippingId = context.getShippingId();
                if (shippingId == null) {
                    return false; // Chưa bắt đầu xử lý shipping
                }
                
                ShippingStatus status = verifyShippingStatus(shippingId);
                return status == ShippingStatus.FAILED || status == ShippingStatus.UNKNOWN;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking if saga needs compensation: {}", saga.getId(), e);
            return true; // Nếu có lỗi, nên thực hiện compensation để an toàn
        }
    }
    
    /**
     * Trạng thái của payment
     */
    public enum PaymentStatus {
        COMPLETED,
        PROCESSING,
        FAILED,
        UNKNOWN
    }
    
    /**
     * Trạng thái của inventory
     */
    public enum InventoryStatus {
        RESERVED,
        PROCESSING,
        FAILED,
        UNKNOWN
    }
    
    /**
     * Trạng thái của shipping
     */
    public enum ShippingStatus {
        SCHEDULED,
        PROCESSING,
        FAILED,
        UNKNOWN
    }
} 