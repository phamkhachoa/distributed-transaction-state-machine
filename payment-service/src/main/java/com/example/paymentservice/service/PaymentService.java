package com.example.paymentservice.service;

import com.example.common.dto.SagaCommand;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment processPayment(SagaCommand command) {
        Map<String, Object> payload = command.getPayload();
        Integer orderId = (Integer) payload.get("id");
        BigDecimal price = new BigDecimal(payload.get("price").toString());

        if (price.compareTo(new BigDecimal("1000")) > 0) {
            throw new RuntimeException("Payment amount exceeds limit for order " + orderId);
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(price)
                .status("COMPLETED")
                .build();
        return paymentRepository.save(payment);
    }

    @Transactional
    public void processRefund(SagaCommand command) {
        Map<String, Object> payload = command.getPayload();
        Integer paymentId = (Integer) payload.get("paymentId");
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found for id: " + paymentId));
        payment.setStatus("REFUNDED");
        paymentRepository.save(payment);
    }
}
