package com.example.orderservice.orchestrator;

import com.example.orderservice.config.RabbitConfig;
import com.example.orderservice.dto.SagaReply;
import com.example.orderservice.service.OrderService;
import io.seata.saga.engine.StateMachineEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaReplyListener {

    private final StateMachineEngine stateMachineEngine;
    private final OrderService orderService;

    @RabbitListener(queues = RabbitConfig.SAGA_REPLY_QUEUE)
    public void onSagaReply(SagaReply reply) {
        log.info("Received saga reply: {}", reply);

        Map<String, Object> context = new HashMap<>();

        if (reply.isSuccess()) {
            context.put("sagaReply", reply);
            stateMachineEngine.forward(reply.getSagaId(), context);
        } else {
            log.error("Saga step failed. SagaId: {}, StateId: {}, Reason: {}",
                    reply.getSagaId(), reply.getStateId(), reply.getFailureReason());
            
            // Trigger compensation
            try {
                Map<String, Object> params = new HashMap<>();
                // Extract orderId from the original start parameters
                orderService.getOrder(reply.getSagaId())
                        .ifPresent(order -> params.put("orderId", order.getId()));

                stateMachineEngine.compensate(reply.getSagaId(), params);
                orderService.updateOrderStatus(reply.getSagaId(), "FAILED");
            } catch (Exception e) {
                log.error("Failed to trigger compensation for sagaId: {}", reply.getSagaId(), e);
            }
        }
    }
} 