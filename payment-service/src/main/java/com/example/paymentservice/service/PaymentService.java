package com.example.paymentservice.service;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public Payment processPayment(String orderId, BigDecimal amount) {
        // Simulate payment processing
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status("COMPLETED")
                .timestamp(LocalDateTime.now())
                .build();
        
        return paymentRepository.save(payment);
    }

    public void processRefund(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (!"COMPLETED".equals(payment.getStatus())) {
            throw new IllegalStateException("Cannot refund a payment that is not completed");
        }

        payment.setStatus("REFUNDED");
        paymentRepository.save(payment);
    }
} 