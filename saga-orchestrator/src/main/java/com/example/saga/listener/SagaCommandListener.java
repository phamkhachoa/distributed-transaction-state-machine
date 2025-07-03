package com.example.saga.listener;

import com.example.saga.config.MessageConfig;
import com.example.saga.model.SagaContext;
import com.example.saga.service.SagaOrchestrationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaCommandListener {

    private final SagaOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = MessageConfig.SAGA_START_QUEUE)
    public void onSagaStart(String message) {
        try {
            log.info("Received saga start command: {}", message);
            Map<String, Object> command = objectMapper.readValue(message, new TypeReference<>() {});
            
            String sagaId = (String) command.get("sagaId");
            Map<String, Object> payload = (Map<String, Object>) command.get("payload");

            SagaContext sagaContext = SagaContext.builder()
                    .sagaId(sagaId)
                    .payload(payload)
                    .build();
            
            orchestrationService.startSaga("ORDER_SAGA", sagaContext);

        } catch (Exception e) {
            log.error("Error processing saga start command", e);
        }
    }
} 