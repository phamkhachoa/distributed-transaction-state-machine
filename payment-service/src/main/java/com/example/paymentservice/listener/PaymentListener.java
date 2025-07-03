package com.example.paymentservice.listener;

import com.example.paymentservice.config.RabbitConfig;
import com.example.paymentservice.dto.SagaCommand;
import com.example.paymentservice.dto.SagaReply;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final PaymentService paymentService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.PAYMENT_COMMAND_QUEUE)
    public void onPaymentCommand(SagaCommand command) {
        log.info("Received command: {}", command);

        SagaReply.SagaReplyBuilder replyBuilder = SagaReply.builder()
                .sagaId(command.getSagaId())
                .stateId(command.getStateId());

        try {
            Map<String, Object> payload = command.getPayload();
            String orderId = (String) payload.get("orderId");

            if (RabbitConfig.PAYMENT_CHARGE_ROUTING_KEY.equals(command.getType())) {
                BigDecimal amount = new BigDecimal((String) payload.get("amount"));
                paymentService.processPayment(orderId, amount);
            } else if (RabbitConfig.PAYMENT_REFUND_ROUTING_KEY.equals(command.getType())) {
                paymentService.refundPayment(orderId);
            }
            
            replyBuilder.success(true);
            
        } catch (Exception e) {
            log.error("Error processing payment command, rejecting message.", e);
            replyBuilder.success(false).failureReason(e.getMessage());
            throw new AmqpRejectAndDontRequeueException("Error processing payment: " + e.getMessage());
        }

        SagaReply reply = replyBuilder.build();
        log.info("Sending reply: {}", reply);
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_REPLY_EXCHANGE, RabbitConfig.SAGA_REPLY_QUEUE_ROUTING_KEY, reply);
    }
} 