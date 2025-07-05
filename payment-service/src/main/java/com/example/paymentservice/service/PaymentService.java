package com.example.paymentservice.service;

import com.example.common.dto.SagaCommand;
import com.example.common.idempotency.IdempotencyService;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;

    @Transactional
    public Payment processPayment(SagaCommand command) {
        String requestId = command.getRequestId();
        String sagaId = command.getSagaId();
        Map<String, Object> payload = command.getPayload();
        Integer orderId = (Integer) payload.get("id");
        
        // Kiểm tra xem payment đã được xử lý chưa
        Optional<Payment> existingPayment = paymentRepository.findByOrderIdAndRequestId(orderId, requestId);
        if (existingPayment.isPresent()) {
            log.info("Payment already processed for order: {} with requestId: {}", orderId, requestId);
            return existingPayment.get();
        }
        
        // Thực hiện xử lý payment với idempotency
        return idempotencyService.executeWithIdempotency(
                requestId,
                sagaId,
                "payment-service",
                "process-payment",
                () -> {
                    BigDecimal price = new BigDecimal(payload.get("price").toString());
                    
                    // Kiểm tra logic nghiệp vụ
                    if (price.compareTo(new BigDecimal("1000")) > 0) {
                        throw new RuntimeException("Payment amount exceeds limit for order " + orderId);
                    }
                    
                    // Tạo payment mới
                    Payment payment = Payment.builder()
                            .orderId(orderId)
                            .amount(price)
                            .status("COMPLETED")
                            .requestId(requestId)
                            .sagaId(sagaId)
                            .build();
                    
                    return paymentRepository.save(payment);
                });
    }

    @Transactional
    public void processRefund(SagaCommand command) {
        String requestId = command.getRequestId();
        String sagaId = command.getSagaId();
        Map<String, Object> payload = command.getPayload();
        Integer paymentId = (Integer) payload.get("paymentId");
        
        // Thực hiện refund với idempotency
        idempotencyService.executeWithIdempotency(
                requestId,
                sagaId,
                "payment-service",
                "process-refund",
                () -> {
                    Payment payment = paymentRepository.findById(paymentId.longValue())
                            .orElseThrow(() -> new RuntimeException("Payment not found for id: " + paymentId));
                    payment.setStatus("REFUNDED");
                    return paymentRepository.save(payment);
                });
    }
}
