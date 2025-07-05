package com.example.saga.interceptor;

import com.example.saga.model.SagaContext;
import com.example.saga.persistence.SagaHistory;
import com.example.saga.persistence.SagaHistoryRepository;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Interceptor để lưu trữ trạng thái saga vào database
 * Sử dụng StateMachineInterceptorAdapter để can thiệp vào quá trình chuyển trạng thái
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaPersistenceInterceptor<S, E> extends StateMachineInterceptorAdapter<S, E> {

    private final SagaHistoryRepository historyRepository;
    private final SagaInstanceRepository instanceRepository;
    private final ObjectMapper objectMapper;
    private static final String INSTANCE_ID = System.getenv().getOrDefault("HOSTNAME", "unknown");
    private static final String SAGA_CONTEXT_KEY = "sagaContext";

    /**
     * Được gọi sau khi state machine thay đổi trạng thái
     * Đây là nơi lý tưởng để lưu trữ trạng thái mới vào database
     */
    @Override
    @Transactional
    public void postStateChange(State<S, E> state, Message<E> message, 
                               Transition<S, E> transition, 
                               StateMachine<S, E> stateMachine,
                               StateMachine<S, E> rootStateMachine) {
        
        if (transition == null || transition.getSource() == null) {
            return;
        }
        
        try {
            // Lấy saga ID từ state machine
            String sagaId = getSagaIdFromStateMachine(stateMachine);
            if (sagaId == null) {
                log.warn("Cannot find saga ID from state machine");
                return;
            }
            
            // Tìm saga instance trong database
            Optional<SagaInstance> sagaInstanceOpt = instanceRepository.findById(sagaId);
            if (sagaInstanceOpt.isEmpty()) {
                log.error("Saga instance not found: {}", sagaId);
                return;
            }
            
            SagaInstance sagaInstance = sagaInstanceOpt.get();
            
            // Lấy thông tin source, target state và event
            String sourceState = transition.getSource().getId().toString();
            String targetState = state.getId().toString();
            String eventName = transition.getTrigger() != null && 
                              transition.getTrigger().getEvent() != null ? 
                              transition.getTrigger().getEvent().toString() : "INTERNAL";
            
            // Lấy kết quả action và lỗi (nếu có)
            Object actionResult = null;
            Object actionError = null;
            try {
                actionResult = stateMachine.getExtendedState().getVariables().get("actionResult");
                actionError = stateMachine.getExtendedState().getVariables().get("actionError");
            } catch (Exception e) {
                log.debug("Could not get action result/error", e);
            }
            
            // Tạo history entry
            SagaHistory history = SagaHistory.builder()
                    .sagaId(sagaId)
                    .sagaType(sagaInstance.getSagaType())
                    .sourceState(sourceState)
                    .targetState(targetState)
                    .event(eventName)
                    .actionResult(actionResult != null ? objectMapper.writeValueAsString(actionResult) : null)
                    .errorMessage(actionError != null ? actionError.toString() : null)
                    .executionTimeMs(0L) // Không thể tính thời gian trong interceptor
                    .instanceId(INSTANCE_ID)
                    .isCompensation(isCompensationTransition(sourceState, targetState))
                    .createdAt(LocalDateTime.now())
                    .build();
            
            historyRepository.save(history);
            
            // Thêm checkpoint vào saga instance
            try {
                sagaInstance.addCheckpoint(
                        targetState,
                        eventName,
                        actionResult != null ? objectMapper.writeValueAsString(actionResult) : null
                );
            } catch (Exception e) {
                log.error("Error adding checkpoint", e);
            }
            
            // Cập nhật trạng thái saga instance
            sagaInstance.setCurrentState(targetState);
            sagaInstance.setUpdatedAt(LocalDateTime.now());
            instanceRepository.save(sagaInstance);
            
            log.info("Persisted state change for saga {}: {} -> {} by event {}", 
                    sagaId, sourceState, targetState, eventName);
            
        } catch (Exception e) {
            log.error("Error persisting state change", e);
        }
    }
    
    /**
     * Được gọi trước khi state machine thay đổi trạng thái
     * Có thể sử dụng để kiểm tra điều kiện trước khi chuyển trạng thái
     */
    @Override
    public Message<E> preEvent(Message<E> message, StateMachine<S, E> stateMachine) {
        if (message != null && message.getPayload() != null) {
            log.debug("Pre-event: {}", message.getPayload());
        }
        return message;
    }
    
    /**
     * Lấy saga ID từ state machine
     */
    private String getSagaIdFromStateMachine(StateMachine<S, E> stateMachine) {
        // Thử lấy saga ID từ state machine ID
        String sagaId = stateMachine.getId();
        
        // Nếu không có, thử lấy từ extended state
        if (sagaId == null || "unknown".equals(sagaId)) {
            try {
                Object sagaContext = stateMachine.getExtendedState().getVariables().get(SAGA_CONTEXT_KEY);
                if (sagaContext instanceof SagaContext) {
                    sagaId = ((SagaContext) sagaContext).getSagaId();
                }
            } catch (Exception e) {
                log.debug("Could not get saga context from extended state", e);
            }
        }
        
        return sagaId;
    }
    
    /**
     * Kiểm tra xem transition có phải là compensation hay không
     */
    private boolean isCompensationTransition(String sourceState, String targetState) {
        return targetState.startsWith("COMPENSATING_") || 
               sourceState.startsWith("COMPENSATING_");
    }
} 