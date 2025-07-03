package com.example.orderservice.orchestrator;

import com.example.orderservice.dto.SagaCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private static final String SAGA_COMMAND_EXCHANGE = "saga-commands";
    private static final String SAGA_START_ROUTING_KEY = "saga.start";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void startSaga(String sagaId, Map<String, Object> payload) {
        try {
            log.info("Starting SAGA with ID: {}", sagaId);

            SagaCommand command = SagaCommand.builder()
                    .sagaId(sagaId)
                    .service("order")
                    .action("START_SAGA")
                    .payload(payload)
                    .build();

            String message = objectMapper.writeValueAsString(command);
            rabbitTemplate.convertAndSend(SAGA_COMMAND_EXCHANGE, SAGA_START_ROUTING_KEY, message);

            log.info("Saga start message sent for sagaId: {}", sagaId);
        } catch (Exception e) {
            log.error("Error starting saga for sagaId: {}", sagaId, e);
        }
    }
} 