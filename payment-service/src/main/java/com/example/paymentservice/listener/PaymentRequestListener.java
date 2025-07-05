package com.example.paymentservice.listener;

import com.example.common.dto.SagaCommand;
import com.example.common.dto.SagaReply;
import com.example.common.idempotency.ProcessedMessageRepository;
import com.example.common.outbox.OutboxMessage;
import com.example.common.outbox.OutboxService;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.service.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequestListener {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "payment-requests")
    @Transactional
    public void handleRequest(SagaCommand command) {
        String requestId = command.getRequestId();
        String sagaId = command.getSagaId();
        String action = command.getAction();
        
        // Kiểm tra xem request đã được xử lý chưa
        if (processedMessageRepository.existsByRequestId(requestId)) {
            log.info("Duplicate request detected: {}", requestId);
            
            // Tìm payment đã xử lý trước đó
            Optional<Payment> existingPayment = paymentRepository.findByRequestId(requestId);
            if (existingPayment.isPresent()) {
                // Gửi lại response với kết quả đã có
                Map<String, Object> payload = new HashMap<>(command.getPayload());
                payload.put("paymentId", existingPayment.get().getId());
                payload.put("status", existingPayment.get().getStatus());
                
                SagaReply reply = SagaReply.builder()
                        .sagaId(sagaId)
                        .requestId(requestId)
                        .success(true)
                        .payload(payload)
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                saveReplyToOutbox("payment.succeeded", reply);
            }
            return;
        }
        
        SagaReply reply;
        try {
            if ("PROCESS_PAYMENT".equals(action)) {
                Payment payment = paymentService.processPayment(command);
                
                Map<String, Object> payload = new HashMap<>(command.getPayload());
                payload.put("paymentId", payment.getId());
                payload.put("status", payment.getStatus());
                
                reply = SagaReply.builder()
                        .sagaId(sagaId)
                        .requestId(requestId)
                        .success(true)
                        .payload(payload)
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                saveReplyToOutbox("payment.succeeded", reply);
            } else if ("COMPENSATE_PAYMENT".equals(action)) {
                paymentService.processRefund(command);
                
                reply = SagaReply.builder()
                        .sagaId(sagaId)
                        .requestId(requestId)
                        .success(true)
                        .payload(command.getPayload())
                        .timestamp(System.currentTimeMillis())
                        .build();
                
                saveReplyToOutbox("payment.compensated", reply);
            } else {
                throw new IllegalArgumentException("Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Failed to process request for sagaId: {}", sagaId, e);
            
            reply = SagaReply.builder()
                    .sagaId(sagaId)
                    .requestId(requestId)
                    .success(false)
                    .reason(e.getMessage())
                    .payload(command.getPayload())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            saveReplyToOutbox("payment.failed", reply);
        }
    }

    private void saveReplyToOutbox(String routingKey, SagaReply reply) {
        try {
            OutboxMessage outboxMessage = OutboxMessage.builder()
                    .aggregateType("PAYMENT")
                    .aggregateId(reply.getSagaId())
                    .eventType(routingKey)
                    .payload(objectMapper.writeValueAsString(reply))
                    .exchange("saga-replies")
                    .routingKey(routingKey)
                    .idempotencyKey(reply.getRequestId())  // Sử dụng requestId làm idempotency key
                    .build();
            outboxService.saveMessage(outboxMessage);
        } catch (JsonProcessingException e) {
            log.error("Error serializing saga reply for sagaId: {}", reply.getSagaId(), e);
        }
    }
} 