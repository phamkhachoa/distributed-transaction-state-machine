package com.example.paymentservice.service;

import com.example.paymentservice.model.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment processPayment(String orderId, BigDecimal amount) {
        // Simulate payment failure for amounts less than 10 for testing purposes
        if (amount.compareTo(BigDecimal.TEN) < 0) {
            Payment payment = Payment.builder()
                    .orderId(orderId)
                    .amount(amount)
                    .status("FAILED")
                    .build();
            paymentRepository.save(payment);
            throw new RuntimeException("Payment failed due to insufficient amount.");
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status("COMPLETED")
                .build();
        return paymentRepository.save(payment);
    }

    @Transactional
    public Optional<Payment> refundPayment(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(payment -> {
                    payment.setStatus("REFUNDED");
                    return paymentRepository.save(payment);
                });
    }
} 