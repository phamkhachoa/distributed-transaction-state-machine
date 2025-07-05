package com.example.saga.service;

import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.persistence.SagaCheckpoint;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service để phục hồi saga khi hệ thống khởi động lại sau khi bị crash
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaRecoveryOnStartupService {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaOrchestrationService orchestrationService;
    private final ExternalStateVerificationService stateVerificationService;
    private static final int MAX_RECOVERY_ATTEMPTS = 3;
    
    /**
     * Phương thức này được gọi khi ứng dụng khởi động xong
     * Tìm và phục hồi các saga đang trong trạng thái IN_PROGRESS
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverSagasOnStartup() {
        log.info("Starting saga recovery after system startup...");
        
        List<SagaInstance> activeSagas = sagaInstanceRepository.findByStatus("IN_PROGRESS");
        log.info("Found {} active sagas that need recovery", activeSagas.size());
        
        for (SagaInstance saga : activeSagas) {
            try {
                recoverSaga(saga);
            } catch (Exception e) {
                log.error("Failed to recover saga: {}", saga.getId(), e);
                markSagaForManualIntervention(saga, "Recovery failed on startup: " + e.getMessage());
            }
        }
    }
    
    /**
     * Phục hồi một saga cụ thể
     */
    @Transactional
    public void recoverSaga(SagaInstance saga) {
        log.info("Recovering saga: {} in state: {}", saga.getId(), saga.getCurrentState());
        
        // Kiểm tra xem saga có bị timeout không
        if (saga.getTimeoutAt() != null && LocalDateTime.now().isAfter(saga.getTimeoutAt())) {
            log.warn("Saga {} has timed out, initiating compensation", saga.getId());
            initiateCompensation(saga);
            return;
        }
        
        // Kiểm tra trạng thái thực tế của các service bên ngoài
        boolean needsCompensation = stateVerificationService.needsCompensation(saga);
        if (needsCompensation) {
            log.warn("Saga {} needs compensation based on external service state verification", saga.getId());
            initiateCompensation(saga);
            return;
        }
        
        // Lấy checkpoint cuối cùng hợp lệ
        SagaCheckpoint lastCheckpoint = getLastValidCheckpoint(saga);
        
        if (lastCheckpoint == null) {
            log.warn("No valid checkpoint found for saga: {}, initiating compensation", saga.getId());
            initiateCompensation(saga);
            return;
        }
        
        // Kiểm tra số lần retry
        if (saga.canRetry(MAX_RECOVERY_ATTEMPTS)) {
            resumeFromCheckpoint(saga, lastCheckpoint);
        } else {
            log.warn("Saga {} exceeded max recovery attempts, initiating compensation", saga.getId());
            initiateCompensation(saga);
        }
    }
    
    /**
     * Tiếp tục saga từ checkpoint
     */
    private void resumeFromCheckpoint(SagaInstance saga, SagaCheckpoint checkpoint) {
        try {
            log.info("Resuming saga {} from state: {}", saga.getId(), checkpoint.getState());
            
            // Cập nhật trạng thái saga
            saga.setCurrentState(checkpoint.getState());
            saga.incrementRetryCount();
            saga.updateNextRetryTime(30); // Đợi 30 giây trước khi retry tiếp
            saga.addMetadata("recovery_timestamp", LocalDateTime.now().toString());
            saga.addMetadata("recovery_reason", "System restart");
            sagaInstanceRepository.save(saga);
            
            // Khôi phục state machine
            orchestrationService.reconstructStateMachine(saga.getId(), checkpoint.getState());
            
            // Tiếp tục xử lý
            orchestrationService.sendEvent(saga.getId(), SagaEvents.RESUME);
            
            log.info("Successfully resumed saga {} from checkpoint", saga.getId());
        } catch (Exception e) {
            log.error("Failed to resume saga: {}", saga.getId(), e);
            if (!saga.canRetry(MAX_RECOVERY_ATTEMPTS)) {
                initiateCompensation(saga);
            }
        }
    }
    
    /**
     * Bắt đầu quy trình compensation
     */
    private void initiateCompensation(SagaInstance saga) {
        try {
            log.info("Initiating compensation for saga: {}", saga.getId());
            
            // Cập nhật trạng thái saga
            saga.setStatus("COMPENSATING");
            saga.addMetadata("compensation_timestamp", LocalDateTime.now().toString());
            saga.addMetadata("compensation_reason", "Recovery after system crash");
            sagaInstanceRepository.save(saga);
            
            // Gửi sự kiện COMPENSATE
            orchestrationService.sendEvent(saga.getId(), SagaEvents.COMPENSATE);
            
            log.info("Successfully initiated compensation for saga: {}", saga.getId());
        } catch (Exception e) {
            log.error("Failed to initiate compensation for saga: {}", saga.getId(), e);
            markSagaForManualIntervention(saga, "Compensation failed: " + e.getMessage());
        }
    }
    
    /**
     * Đánh dấu saga cần can thiệp thủ công
     */
    private void markSagaForManualIntervention(SagaInstance saga, String reason) {
        saga.setStatus("MANUAL_INTERVENTION_REQUIRED");
        saga.setErrorMessage(reason);
        saga.addMetadata("manual_intervention_timestamp", LocalDateTime.now().toString());
        sagaInstanceRepository.save(saga);
        
        log.warn("Saga {} requires manual intervention: {}", saga.getId(), reason);
    }
    
    /**
     * Lấy checkpoint hợp lệ cuối cùng
     */
    private SagaCheckpoint getLastValidCheckpoint(SagaInstance saga) {
        if (saga.getCheckpoints() == null || saga.getCheckpoints().isEmpty()) {
            return null;
        }
        
        return saga.getCheckpoints().stream()
                .filter(cp -> cp.getErrorMessage() == null)
                .reduce((first, second) -> second)
                .orElse(null);
    }
} 