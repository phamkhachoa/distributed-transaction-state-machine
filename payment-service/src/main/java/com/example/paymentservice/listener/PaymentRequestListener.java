package com.example.paymentservice.listener;

import com.example.paymentservice.dto.SagaReply;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.service.PaymentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRequestListener {

    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private static final String SAGA_REPLY_QUEUE = "saga-replies";

    @RabbitListener(queues = "payment-requests")
    public void handlePaymentRequest(String message) {
        SagaReply reply;
        String sagaId = null;
        try {
            Map<String, Object> command = objectMapper.readValue(message, new TypeReference<>() {});
            sagaId = (String) command.get("sagaId");
            String action = (String) command.get("action");
            Map<String, Object> payload = (Map<String, Object>) command.get("payload");

            log.info("Received payment request for sagaId: {}, action: {}", sagaId, action);

            if ("PROCESS_PAYMENT".equals(action)) {
                String orderId = (String) payload.get("orderId");
                BigDecimal amount = new BigDecimal(payload.get("amount").toString());
                Payment payment = paymentService.processPayment(orderId, amount);
                payload.put("paymentId", payment.getId());

                reply = SagaReply.builder()
                        .sagaId(sagaId)
                        .service("payment")
                        .status("SUCCESS")
                        .payload(payload)
                        .build();

            } else if ("REFUND_PAYMENT".equals(action)) {
                Long paymentId = Long.parseLong(payload.get("paymentId").toString());
                paymentService.processRefund(paymentId);
                reply = SagaReply.builder()
                        .sagaId(sagaId)
                        .service("payment")
                        .status("SUCCESS")
                        .payload(payload)
                        .build();

            } else {
                throw new IllegalArgumentException("Unknown action: " + action);
            }

        } catch (Exception e) {
            log.error("Error processing payment request for sagaId: {}", sagaId, e);
            reply = SagaReply.builder()
                    .sagaId(sagaId)
                    .service("payment")
                    .status("FAILED")
                    .failureReason(e.getMessage())
                    .build();
        }

        try {
            String replyMessage = objectMapper.writeValueAsString(reply);
            rabbitTemplate.convertAndSend(SAGA_REPLY_QUEUE, replyMessage);
            log.info("Sent payment reply for sagaId: {}", sagaId);
        } catch (Exception e) {
            log.error("Error sending reply for sagaId: {}", sagaId, e);
        }
    }
} 