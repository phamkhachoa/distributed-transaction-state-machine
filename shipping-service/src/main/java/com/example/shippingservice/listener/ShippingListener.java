package com.example.shippingservice.listener;

import com.example.shippingservice.config.RabbitConfig;
import com.example.shippingservice.dto.SagaCommand;
import com.example.shippingservice.dto.SagaReply;
import com.example.shippingservice.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingListener {

    private final ShippingService shippingService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.SHIPPING_COMMAND_QUEUE)
    public void onShippingCommand(SagaCommand command) {
        log.info("Received command: {}", command);

        SagaReply.SagaReplyBuilder replyBuilder = SagaReply.builder()
                .sagaId(command.getSagaId())
                .stateId(command.getStateId());

        try {
            Map<String, Object> payload = command.getPayload();
            String orderId = (String) payload.get("orderId");

            if (RabbitConfig.SHIPPING_SCHEDULE_ROUTING_KEY.equals(command.getType())) {
                String customerId = (String) payload.get("customerId");
                shippingService.scheduleShipping(orderId, customerId);
            } else if (RabbitConfig.SHIPPING_CANCEL_ROUTING_KEY.equals(command.getType())) {
                shippingService.cancelShipping(orderId);
            }

            replyBuilder.success(true);

        } catch (Exception e) {
            log.error("Error processing shipping command", e);
            replyBuilder.success(false).failureReason(e.getMessage());
        }

        SagaReply reply = replyBuilder.build();
        log.info("Sending reply: {}", reply);
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_REPLY_EXCHANGE, RabbitConfig.SAGA_REPLY_QUEUE_ROUTING_KEY, reply);
    }
} 