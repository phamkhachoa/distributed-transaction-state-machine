package com.example.saga.service;

import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for saga recovery and monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaRecoveryService {

    private final SagaOrchestrationService orchestrationService;
    private final SagaInstanceRepository sagaInstanceRepository;
    
    // Check for stuck sagas every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void checkStuckSagas() {
        log.debug("Checking for stuck sagas...");
        
        // Find sagas that haven't been updated in the last 30 minutes
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        List<SagaInstance> stuckSagas = sagaInstanceRepository.findStuckSagas(cutoffTime, "ACTIVE");
        
        for (SagaInstance saga : stuckSagas) {
            log.warn("Found stuck saga: {} in state: {}", saga.getId(), saga.getCurrentState());
            recoverSaga(saga);
        }
    }
    
    // Check for failed sagas every hour
    @Scheduled(fixedRate = 3600000)
    public void checkFailedSagas() {
        log.debug("Checking for failed sagas...");
        
        List<SagaInstance> failedSagas = sagaInstanceRepository.findByStatus("FAILED");
        
        for (SagaInstance saga : failedSagas) {
            log.warn("Found failed saga: {} - considering compensation", saga.getId());
            compensateFailedSaga(saga);
        }
    }
    
    /**
     * Attempt to recover a stuck saga
     */
    public boolean recoverSaga(SagaInstance instance) {
        log.info("Attempting to recover saga: {}", instance.getId());
        
        try {
            // Find saga context and current state
            String sagaId = instance.getId();
            SagaContext context = (SagaContext) orchestrationService.getSagaContext(sagaId);
            
            // Start new saga with context to resume
            orchestrationService.startSaga("ORDER_SAGA", context);
            
            // Resume the saga from the current state
            orchestrationService.sendEvent(sagaId, SagaEvents.START_SAGA);
            
            // Update instance status
            instance.setStatus("ACTIVE");
            sagaInstanceRepository.save(instance);
            
            log.info("Successfully recovered saga: {}", sagaId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to recover saga: {}", instance.getId(), e);
            return false;
        }
    }
    
    /**
     * Start compensation for a failed saga
     */
    private void compensateFailedSaga(SagaInstance saga) {
        String lastSuccessfulState = getLastSuccessfulState(saga);
        
        try {
            // Find saga context
            String sagaId = saga.getId();
            SagaContext context = (SagaContext) orchestrationService.getSagaContext(sagaId);
            
            // Start compensation from the current state
            orchestrationService.sendEvent(sagaId, SagaEvents.SAGA_FAILED);
            
            // Update instance status
            saga.setStatus("COMPENSATING");
            sagaInstanceRepository.save(saga);
            
            log.info("Started compensation for saga: {}", sagaId);
            
        } catch (Exception e) {
            log.error("Failed to start compensation for saga: {}", saga.getId(), e);
        }
    }
    
    private String getLastSuccessfulState(SagaInstance saga) {
        // Get the last successful state from checkpoints
        if (saga.getCheckpoints() != null && !saga.getCheckpoints().isEmpty()) {
            return saga.getCheckpoints().get(saga.getCheckpoints().size() - 1).getState();
        }
        return "STARTED";
    }
} 
