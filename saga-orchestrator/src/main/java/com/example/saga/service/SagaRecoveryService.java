package com.example.saga.service;

import com.example.saga.model.SagaStates;
import com.example.saga.persistence.SagaCheckpoint;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaRecoveryService {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaOrchestrationService orchestrationService;
    
    private static final long STUCK_SAGA_THRESHOLD_MINUTES = 30;
    private static final int MAX_RECOVERY_ATTEMPTS = 3;

    /**
     * Scheduled job to detect and recover stuck sagas
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void recoverStuckSagas() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_SAGA_THRESHOLD_MINUTES);
        
        List<SagaInstance> stuckSagas = sagaInstanceRepository.findStuckSagas(
            threshold, "IN_PROGRESS");
            
        log.info("Found {} stuck sagas for recovery", stuckSagas.size());
        
        for (SagaInstance saga : stuckSagas) {
            try {
                recoverSaga(saga);
            } catch (Exception e) {
                log.error("Failed to recover saga: {}", saga.getId(), e);
            }
        }
    }

    /**
     * Recover a specific saga instance
     */
    @Transactional
    public void recoverSaga(SagaInstance saga) {
        log.info("Starting recovery for saga: {}", saga.getId());
        
        // Get last valid checkpoint
        SagaCheckpoint lastValidCheckpoint = getLastValidCheckpoint(saga);
        
        if (lastValidCheckpoint == null) {
            log.warn("No valid checkpoint found for saga: {}, initiating compensation", 
                saga.getId());
            initiateCompensation(saga);
            return;
        }
        
        // Check if we should retry or compensate
        if (saga.canRetry(MAX_RECOVERY_ATTEMPTS)) {
            retryFromCheckpoint(saga, lastValidCheckpoint);
        } else {
            log.warn("Saga {} exceeded max recovery attempts, initiating compensation", 
                saga.getId());
            initiateCompensation(saga);
        }
    }

    private SagaCheckpoint getLastValidCheckpoint(SagaInstance saga) {
        return saga.getCheckpoints().stream()
            .filter(cp -> cp.getErrorMessage() == null)
            .reduce((first, second) -> second)
            .orElse(null);
    }

    private void retryFromCheckpoint(SagaInstance saga, SagaCheckpoint checkpoint) {
        log.info("Retrying saga {} from state: {}", saga.getId(), checkpoint.getState());
        
        // Update saga state
        saga.setCurrentState(checkpoint.getState());
        saga.incrementRetryCount();
        sagaInstanceRepository.save(saga);
        
        // Reconstruct state machine
        orchestrationService.reconstructStateMachine(saga.getId(), checkpoint.getState());
        
        // Resume processing
        try {
            orchestrationService.resumeSaga(saga.getId());
            log.info("Successfully resumed saga {} from checkpoint", saga.getId());
        } catch (Exception e) {
            log.error("Failed to resume saga: {}", saga.getId(), e);
            if (!saga.canRetry(MAX_RECOVERY_ATTEMPTS)) {
                initiateCompensation(saga);
            }
        }
    }

    private void initiateCompensation(SagaInstance saga) {
        log.info("Initiating compensation for saga: {}", saga.getId());
        
        // Find the last successful state to start compensation from
        String lastSuccessfulState = getLastValidCheckpoint(saga).getState();
        
        // Start compensation process
        saga.startCompensation(lastSuccessfulState);
        sagaInstanceRepository.save(saga);
        
        try {
            // Trigger compensation flow in state machine
            orchestrationService.startCompensation(saga.getId(), lastSuccessfulState);
            log.info("Successfully started compensation for saga: {}", saga.getId());
        } catch (Exception e) {
            log.error("Failed to start compensation for saga: {}", saga.getId(), e);
            // Mark as failed and requiring manual intervention
            saga.setStatus("FAILED_COMPENSATION_REQUIRED");
            saga.setErrorMessage("Failed to start compensation: " + e.getMessage());
            sagaInstanceRepository.save(saga);
        }
    }

    /**
     * Check health of active sagas
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void checkSagaHealth() {
        List<SagaInstance> activeSagas = sagaInstanceRepository
            .findByStatus("IN_PROGRESS");
            
        for (SagaInstance saga : activeSagas) {
            if (isStuck(saga)) {
                log.warn("Detected potentially stuck saga: {}", saga.getId());
                // Add to monitoring queue
                monitorSaga(saga);
            }
        }
    }

    private boolean isStuck(SagaInstance saga) {
        LocalDateTime lastUpdate = saga.getUpdatedAt();
        LocalDateTime threshold = LocalDateTime.now()
            .minusMinutes(STUCK_SAGA_THRESHOLD_MINUTES);
        return lastUpdate.isBefore(threshold);
    }

    private void monitorSaga(SagaInstance saga) {
        // Add monitoring metadata
        saga.addMetadata("monitoring_start", LocalDateTime.now().toString());
        saga.addMetadata("monitoring_reason", "Potential stuck state detected");
        sagaInstanceRepository.save(saga);
        
        // Could integrate with external monitoring system here
    }
} 