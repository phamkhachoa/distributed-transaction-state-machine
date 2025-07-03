package com.example.saga.controller;

import com.example.saga.dto.SagaResponse;
import com.example.saga.dto.StartSagaRequest;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import com.example.saga.service.SagaOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST API for saga management
 */
@Slf4j
@RestController
@RequestMapping("/api/sagas")
@RequiredArgsConstructor
public class SagaController {

    private final SagaOrchestrationService orchestrationService;
    private final SagaInstanceRepository sagaInstanceRepository;

    /**
     * Start a new saga
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> startSaga(@RequestBody SagaContext sagaContext) {
        log.info("Starting new saga for order: {}", sagaContext.getPayload().get("orderId"));
        
        // Start the saga with the ORDER_SAGA type
        String sagaId = orchestrationService.startSaga("ORDER_SAGA", (Object) sagaContext);
        
        // Send the initial event to start the saga
        orchestrationService.sendEvent(sagaId, SagaEvents.START_SAGA);
        
        Map<String, String> response = new HashMap<>();
        response.put("sagaId", sagaId);
        response.put("message", "Saga started successfully");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get saga status by ID
     */
    @GetMapping("/{sagaId}")
    public ResponseEntity<Map<String, Object>> getSagaStatus(@PathVariable String sagaId) {
        log.info("Getting saga status for: {}", sagaId);
        
        // Get saga instance
        SagaInstance instance = sagaInstanceRepository.findById(sagaId).orElse(null);
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Get saga context
        SagaContext context = (SagaContext) orchestrationService.getSagaContext(sagaId);
        
        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("sagaId", sagaId);
        response.put("sagaType", instance.getSagaType());
        response.put("currentState", instance.getCurrentState());
        response.put("status", instance.getStatus());
        response.put("createdAt", instance.getCreatedAt());
        response.put("updatedAt", instance.getUpdatedAt());
        if (context != null && context.getPayload() != null) {
            response.put("orderId", context.getPayload().get("orderId"));
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all sagas for an order
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Map<String, Object>>> getSagasByOrderId(@PathVariable String orderId) {
        log.info("Getting sagas for order: {}", orderId);

        List<SagaInstance> instances = orchestrationService.findSagasByMetadata("orderId", orderId);

        // Map to response format
        List<Map<String, Object>> response = instances.stream()
                .map(instance -> {
                    Map<String, Object> sagaInfo = new HashMap<>();
                    sagaInfo.put("sagaId", instance.getId());
                    sagaInfo.put("sagaType", instance.getSagaType());
                    sagaInfo.put("currentState", instance.getCurrentState());
                    sagaInfo.put("status", instance.getStatus());
                    sagaInfo.put("createdAt", instance.getCreatedAt());
                    sagaInfo.put("updatedAt", instance.getUpdatedAt());

                    // Get saga context and populate from payload
                    SagaContext context = (SagaContext) orchestrationService.getSagaContext(instance.getId());
                    if (context != null && context.getPayload() != null) {
                        sagaInfo.putAll(context.getPayload());
                    }

                    return sagaInfo;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
    
    /**
     * Get all active sagas
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllActiveSagas() {
        log.info("Getting all active sagas");
        
        // Get all active sagas
        List<SagaInstance> instances = sagaInstanceRepository.findByStatus("IN_PROGRESS");
        
        // Map to response format
        List<Map<String, Object>> response = instances.stream()
                .map(instance -> {
                    Map<String, Object> sagaInfo = new HashMap<>();
                    sagaInfo.put("sagaId", instance.getId());
                    sagaInfo.put("sagaType", instance.getSagaType());
                    sagaInfo.put("currentState", instance.getCurrentState());
                    sagaInfo.put("status", instance.getStatus());
                    sagaInfo.put("createdAt", instance.getCreatedAt());
                    sagaInfo.put("updatedAt", instance.getUpdatedAt());
                    
                    SagaContext context = (SagaContext) orchestrationService.getSagaContext(instance.getId());
                    if (context != null && context.getPayload() != null) {
                        sagaInfo.put("orderId", context.getPayload().get("orderId"));
                    }
                    return sagaInfo;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    private SagaResponse buildSagaResponse(SagaInstance instance, SagaContext context) {
        Map<String, Object> payload = context.getPayload() != null ? context.getPayload() : new HashMap<>();
        
        Object lastShippingUpdate = payload.get("lastShippingUpdate");
        LocalDateTime lastShippingUpdateTimestamp = null;
        if (lastShippingUpdate != null) {
            lastShippingUpdateTimestamp = LocalDateTime.parse(lastShippingUpdate.toString());
        }

        return SagaResponse.builder()
                .sagaId(instance.getId())
                .orderId(String.valueOf(payload.get("orderId")))
                .currentState(SagaStates.valueOf(instance.getCurrentState()))
                .status(instance.getStatus())
                .errorMessage(instance.getErrorMessage())
                .startTime((LocalDateTime) payload.get("startTime"))
                .lastUpdateTime(instance.getUpdatedAt())
                .metadata(payload)
                .shippingId((String) payload.get("shippingId"))
                .lastShippingStatus((String) payload.get("lastShippingStatus"))
                .lastShippingUpdate(lastShippingUpdateTimestamp)
                .build();
    }
} 