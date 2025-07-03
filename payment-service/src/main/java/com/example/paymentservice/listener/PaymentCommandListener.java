package com.example.paymentservice.listener;

import com.example.paymentservice.config.RabbitConfig;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCommandListener {

    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.PAYMENT_COMMAND_QUEUE)
    public void handleCommand(Map<String, Object> command) {
        String sagaId = (String) command.get("sagaId");
        String orderId = (String) command.get("orderId");
        String action = (String) command.get("action");

        log.info("Received payment command: {} for saga: {}, order: {}", action, sagaId, orderId);

        try {
            Map<String, Object> reply = new HashMap<>();
            reply.put("sagaId", sagaId);
            reply.put("orderId", orderId);
            reply.put("service", "payment");

            switch (action.toUpperCase()) {
                case "CHARGE":
                    handleCharge(command, reply);
                    break;
                    
                case "REFUND":
                    handleRefund(command, reply);
                    break;
                    
                default:
                    reply.put("status", "FAILED");
                    reply.put("errorMessage", "Unknown action: " + action);
            }

            // Send reply
            rabbitTemplate.convertAndSend(
                RabbitConfig.SAGA_REPLY_EXCHANGE,
                "saga.reply." + action.toLowerCase(),
                reply
            );

        } catch (Exception e) {
            log.error("Error processing payment command", e);
            sendErrorReply(sagaId, orderId, action, e.getMessage());
        }
    }

    private void handleCharge(Map<String, Object> command, Map<String, Object> reply) {
        String orderId = (String) command.get("orderId");
        String userId = (String) command.get("userId");
        Double amount = ((Number) command.get("amount")).doubleValue();

        String paymentId = paymentService.processPayment(orderId, userId, amount);

        if (paymentId != null) {
            reply.put("status", "SUCCESS");
            reply.put("paymentId", paymentId);
        } else {
            reply.put("status", "FAILED");
            reply.put("errorMessage", "Payment processing failed");
        }
    }

    private void handleRefund(Map<String, Object> command, Map<String, Object> reply) {
        String orderId = (String) command.get("orderId");
        String paymentId = (String) command.get("paymentId");
        Double amount = ((Number) command.get("amount")).doubleValue();

        boolean refunded = paymentService.processRefund(orderId, paymentId, amount);

        if (refunded) {
            reply.put("status", "REFUNDED");
        } else {
            reply.put("status", "FAILED");
            reply.put("errorMessage", "Refund processing failed");
        }
    }

    private void sendErrorReply(String sagaId, String orderId, String action, String errorMessage) {
        Map<String, Object> errorReply = new HashMap<>();
        errorReply.put("sagaId", sagaId);
        errorReply.put("orderId", orderId);
        errorReply.put("service", "payment");
        errorReply.put("status", "FAILED");
        errorReply.put("errorMessage", errorMessage);

        rabbitTemplate.convertAndSend(
            RabbitConfig.SAGA_REPLY_EXCHANGE,
            "saga.reply." + action.toLowerCase(),
            errorReply
        );
    }
} 