package com.example.paymentservice.repository;

import com.example.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    /**
     * Tìm payment theo orderId và requestId
     */
    Optional<Payment> findByOrderIdAndRequestId(Integer orderId, String requestId);
    
    /**
     * Tìm payment theo sagaId
     */
    Optional<Payment> findBySagaId(String sagaId);
    
    /**
     * Kiểm tra xem payment với requestId đã tồn tại chưa
     */
    boolean existsByRequestId(String requestId);
} 