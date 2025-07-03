package com.example.orderservice.orchestrator;

import com.example.orderservice.config.RabbitConfig;
import com.example.orderservice.dto.SagaCommand;
import io.seata.saga.proctrl.ProcessContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final RabbitTemplate rabbitTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.example.orderservice.service.OrderService orderService;

    private static final String PAYMENT_CHARGE_ROUTING_KEY = "payment.charge";
    private static final String PAYMENT_REFUND_ROUTING_KEY = "payment.refund";
    private static final String SHIPPING_SCHEDULE_ROUTING_KEY = "shipping.schedule";
    private static final String SHIPPING_CANCEL_ROUTING_KEY = "shipping.cancel";
    private static final String PAYMENT_SUCCESS_TOPIC = "payment.success.events";

    public void requestPayment(ProcessContext context) {
        SagaCommand command = buildCommand(context, PAYMENT_CHARGE_ROUTING_KEY);
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_COMMAND_EXCHANGE, PAYMENT_CHARGE_ROUTING_KEY, command);
        log.info("Sent payment request: {}", command);
    }

    public void requestPaymentRefund(ProcessContext context) {
        SagaCommand command = buildCommand(context, PAYMENT_REFUND_ROUTING_KEY);
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_COMMAND_EXCHANGE, PAYMENT_REFUND_ROUTING_KEY, command);
        log.info("Sent payment refund request: {}", command);
    }

    public void requestShipping(ProcessContext context) {
        SagaCommand command = buildCommand(context, SHIPPING_SCHEDULE_ROUTING_KEY);
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_COMMAND_EXCHANGE, SHIPPING_SCHEDULE_ROUTING_KEY, command);
        log.info("Sent shipping request: {}", command);
    }
    
    public void requestShippingCancellation(ProcessContext context) {
        SagaCommand command = buildCommand(context, SHIPPING_CANCEL_ROUTING_KEY);
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_COMMAND_EXCHANGE, SHIPPING_CANCEL_ROUTING_KEY, command);
        log.info("Sent shipping cancellation request: {}", command);
    }

    public void requestEmailNotification(ProcessContext context) {
        Map<String, Object> startParams = (Map<String, Object>) context.getVariable("startParams");
        kafkaTemplate.send(PAYMENT_SUCCESS_TOPIC, startParams.get("orderId").toString(), startParams);
        log.info("Sent email notification request to Kafka with payload: {}", startParams);
    }

    public void markOrderAsCancelled(String orderId) {
        orderService.updateOrderStatus(orderId, "CANCELLED");
        log.info("Saga cancelled orderId: {}", orderId);
    }

    public void completeOrder(String orderId) {
        orderService.updateOrderStatus(orderId, "COMPLETED");
        log.info("Saga completed for orderId: {}", orderId);
    }

    private SagaCommand buildCommand(ProcessContext context, String type) {
        String sagaId = context.getVariable("sagaId").toString();
        String stateId = context.getStateInstance().getId();
        Map<String, Object> startParams = (Map<String, Object>) context.getVariable("startParams");
        return new SagaCommand(sagaId, stateId, type, startParams);
    }
} 