package com.example.saga.service;

import com.example.saga.definition.SagaDefinition;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.model.ShippingContext;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import com.example.saga.registry.SagaRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for orchestrating saga instances
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrationService {
    
    private final SagaRegistry sagaRegistry;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final ObjectMapper objectMapper;
    
    private static final String SAGA_CONTEXT_KEY = "sagaContext";
    private static final long LOCK_TIMEOUT = 30; // seconds
    
    /**
     * Start a new saga instance
     */
    @Transactional
    public <S extends Enum<S>, E extends Enum<E>> String startSaga(String sagaType, Object context) {
        String sagaId = UUID.randomUUID().toString();
        
        try {
            // Get saga definition
            SagaDefinition<S, E> definition = (SagaDefinition<S, E>) sagaRegistry.getSagaDefinition(sagaType);
            
            // Validate context
            definition.validateContext(context);
            
            // Prepare context
            if (context instanceof SagaContext sagaContext) {
                sagaContext.setSagaId(sagaId);
                sagaContext.getPayload().put("startTime", LocalDateTime.now());
                sagaContext.getPayload().put("status", "IN_PROGRESS");
            }
            
            // Create and start state machine
            StateMachine<S, E> stateMachine = definition.getStateMachineFactory().getStateMachine(sagaId);
            stateMachine.getExtendedState().getVariables().put(SAGA_CONTEXT_KEY, context);
            
            // Calculate timeout
            LocalDateTime timeoutAt = LocalDateTime.now().plusMinutes(definition.getTimeoutMinutes());
            
            // Persist saga instance
            SagaInstance sagaInstance = SagaInstance.builder()
                    .id(sagaId)
                    .sagaType(sagaType)
                    .currentState(definition.getInitialState().name())
                    .sagaData(objectMapper.writeValueAsString(context))
                    .status("IN_PROGRESS")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .timeoutAt(timeoutAt)
                    .build();
            
            sagaInstanceRepository.save(sagaInstance);
            
            // Start the state machine
            stateMachine.start();
            
            log.info("Started saga type: {} with ID: {}", sagaType, sagaId);
            return sagaId;
            
        } catch (Exception e) {
            log.error("Error starting saga type: {}", sagaType, e);
            throw new RuntimeException("Failed to start saga", e);
        }
    }
    
    /**
     * Send an event to a saga instance
     */
    @Transactional
    public <S extends Enum<S>, E extends Enum<E>> boolean sendEvent(String sagaId, E event) {
        try {
            // Get saga instance
            SagaInstance sagaInstance = sagaInstanceRepository.findById(sagaId)
                    .orElseThrow(() -> new RuntimeException("Saga instance not found: " + sagaId));
            
            // Check if saga is not timed out
            if (sagaInstance.getTimeoutAt() != null && 
                LocalDateTime.now().isAfter(sagaInstance.getTimeoutAt())) {
                log.warn("Saga {} has timed out", sagaId);
                return false;
            }
            
            // Get saga definition
            SagaDefinition<S, E> definition = (SagaDefinition<S, E>) sagaRegistry
                    .getSagaDefinition(sagaInstance.getSagaType());
            
            // Get state machine
            StateMachine<S, E> stateMachine = definition.getStateMachineFactory().getStateMachine(sagaId);
            
            // Restore state if needed
            if (!stateMachine.getState().getId().name().equals(sagaInstance.getCurrentState())) {
                restoreStateMachine(stateMachine, sagaInstance);
            }
            
            // Send event
            boolean accepted = stateMachine.sendEvent(event);
            
            if (accepted) {
                // Update saga instance
                sagaInstance.setCurrentState(stateMachine.getState().getId().name());
                sagaInstance.setUpdatedAt(LocalDateTime.now());
                sagaInstanceRepository.save(sagaInstance);
                
                log.info("Event {} accepted for saga: {}", event, sagaId);
            } else {
                log.warn("Event {} not accepted for saga: {} in state: {}", 
                        event, sagaId, stateMachine.getState().getId());
            }
            
            return accepted;
            
        } catch (Exception e) {
            log.error("Error sending event {} to saga: {}", event, sagaId, e);
            throw new RuntimeException("Failed to send event to saga", e);
        }
    }
    
    /**
     * Get saga context
     */
    public Object getSagaContext(String sagaId) {
        try {
            SagaInstance sagaInstance = sagaInstanceRepository.findById(sagaId)
                    .orElseThrow(() -> new RuntimeException("Saga instance not found: " + sagaId));
            
            SagaDefinition<?, ?> definition = sagaRegistry.getSagaDefinition(sagaInstance.getSagaType());
            return objectMapper.readValue(sagaInstance.getSagaData(), Object.class);
            
        } catch (Exception e) {
            log.error("Error getting saga context for saga: {}", sagaId, e);
            throw new RuntimeException("Failed to get saga context", e);
        }
    }
    
    /**
     * Update saga context
     */
    @Transactional
    public void updateSagaContext(String sagaId, Object context) {
        try {
            SagaInstance sagaInstance = sagaInstanceRepository.findById(sagaId)
                    .orElseThrow(() -> new RuntimeException("Saga instance not found: " + sagaId));
            
            sagaInstance.setSagaData(objectMapper.writeValueAsString(context));
            sagaInstance.setUpdatedAt(LocalDateTime.now());
            sagaInstanceRepository.save(sagaInstance);
            
            // Also update in state machine
            SagaDefinition<?, ?> definition = sagaRegistry.getSagaDefinition(sagaInstance.getSagaType());
            StateMachine<?, ?> stateMachine = definition.getStateMachineFactory().getStateMachine(sagaId);
            stateMachine.getExtendedState().getVariables().put(SAGA_CONTEXT_KEY, context);
            
        } catch (Exception e) {
            log.error("Error updating saga context for saga: {}", sagaId, e);
            throw new RuntimeException("Failed to update saga context", e);
        }
    }
    
    /**
     * Restore state machine from persisted state
     */
    private <S extends Enum<S>, E extends Enum<E>> void restoreStateMachine(
            StateMachine<S, E> stateMachine, SagaInstance sagaInstance) {
        try {
            SagaDefinition<S, E> definition = (SagaDefinition<S, E>) sagaRegistry
                    .getSagaDefinition(sagaInstance.getSagaType());
            
            @SuppressWarnings("unchecked")
            S enumState = (S) Enum.valueOf(
                    (Class<S>) definition.getInitialState().getClass(),
                    sagaInstance.getCurrentState()
            );
            
            // Restore context
            Object context = objectMapper.readValue(sagaInstance.getSagaData(), Object.class);
            stateMachine.getExtendedState().getVariables().put(SAGA_CONTEXT_KEY, context);
            
            // Reset state machine
            stateMachine.stop();
            stateMachine.getStateMachineAccessor()
                    .doWithAllRegions(access -> {
                        access.resetStateMachine(new DefaultStateMachineContext<>(
                                enumState, null, null, null));
                    });
            
            log.info("Restored state machine {} to state: {}", sagaInstance.getId(), enumState);
            
        } catch (Exception e) {
            log.error("Error restoring state machine", e);
            throw new RuntimeException("Failed to restore state machine", e);
        }
    }
    
    /**
     * Get current state of a saga
     */
    public <S extends Enum<S>, E extends Enum<E>> S getCurrentState(String sagaId) {
        SagaInstance sagaInstance = sagaInstanceRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException("Saga instance not found: " + sagaId));
        
        SagaDefinition<S, E> definition = (SagaDefinition<S, E>) sagaRegistry
                .getSagaDefinition(sagaInstance.getSagaType());
        
        @SuppressWarnings("unchecked")
        Class<S> stateType = (Class<S>) definition.getInitialState().getClass();
        return Enum.valueOf(stateType, sagaInstance.getCurrentState());
    }
    
    /**
     * Find saga instances by metadata
     */
    public List<SagaInstance> findSagasByMetadata(String key, String value) {
        try {
            // Find all active sagas
            List<SagaInstance> activeSagas = sagaInstanceRepository.findByStatusNot("COMPLETED");
            
            // Filter by metadata
            return activeSagas.stream()
                    .filter(saga -> {
                        try {
                            SagaContext sagaContext = objectMapper.readValue(saga.getSagaData(), SagaContext.class);
                            Object metadataValue = sagaContext.getPayload().get(key);
                            return metadataValue != null && metadataValue.toString().equals(value);
                        } catch (Exception e) {
                            log.warn("Could not parse sagaData for sagaId: {}", saga.getId(), e);
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error finding sagas by metadata", e);
            return Collections.emptyList();
        }
    }
} 