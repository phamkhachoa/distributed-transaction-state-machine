//package com.example.saga.listener;
//
//import com.example.saga.model.SagaContext;
//import com.example.saga.model.SagaEvents;
//import com.example.saga.model.SagaStates;
//import com.example.saga.persistence.SagaHistory;
//import com.example.saga.persistence.SagaHistoryRepository;
//import com.example.saga.persistence.SagaInstance;
//import com.example.saga.persistence.SagaInstanceRepository;
//import com.example.saga.service.SagaOrchestrationService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.statemachine.StateMachine;
//import org.springframework.statemachine.StateContext;
//import org.springframework.statemachine.listener.StateMachineListenerAdapter;
//import org.springframework.statemachine.state.State;
//import org.springframework.statemachine.transition.Transition;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class SagaStateChangeListener<S extends Enum<S>, E extends Enum<E>>
//        extends StateMachineListenerAdapter<S, E> {
//
//    private final SagaHistoryRepository historyRepository;
//    private final SagaInstanceRepository instanceRepository;
//    private final ObjectMapper objectMapper;
//    private final SagaOrchestrationService orchestrationService;
//    private static final String INSTANCE_ID = System.getenv().getOrDefault("HOSTNAME", "unknown");
//    private static final String SAGA_CONTEXT_KEY = "sagaContext";
//
//    private ThreadLocal<Long> startTime = new ThreadLocal<>();
//
//    @Override
//    public void stateContext(StateContext<S, E> stateContext) {
//        if (stateContext.getStage() == StateContext.Stage.STATE_ENTRY) {
//            startTime.set(System.currentTimeMillis());
//        }
//    }
//
//    @Override
//    @Transactional
//    public void transition(Transition<S, E> transition) {
//        if (transition == null || transition.getSource() == null || transition.getTarget() == null) {
//            return;
//        }
//
//        try {
//            State<S, E> source = transition.getSource();
//            State<S, E> target = transition.getTarget();
//            E event = transition.getTrigger() != null ? transition.getTrigger().getEvent() : null;
//
//            // Trong Spring State Machine, không có cách trực tiếp để lấy StateMachine từ transition
//            // Thay vào đó, chúng ta sẽ sử dụng thông tin từ transition để tìm saga ID
//
//            // Tạo một ID tạm thời từ source và target state để tìm kiếm trong database
//            String sourceState = source.getId().name();
//            String targetState = target.getId().name();
//            String eventName = event != null ? event.name() : "INTERNAL";
//
//            log.debug("Processing transition: {} -> {} by event {}", sourceState, targetState, eventName);
//
//            // Tìm saga ID từ lịch sử gần đây nhất
//            String sagaId = findSagaIdFromRecentHistory(sourceState, targetState);
//
//            if (sagaId == null) {
//                log.warn("Cannot find saga ID for transition: {} -> {} by event {}",
//                        sourceState, targetState, eventName);
//                // Tiếp tục xử lý với một ID tạm thời để ghi log
//                sagaId = "unknown-" + System.currentTimeMillis();
//            }
//
//            // Tìm saga instance trong database
//            Optional<SagaInstance> sagaInstanceOpt = instanceRepository.findById(sagaId);
//            if (sagaInstanceOpt.isEmpty()) {
//                log.error("Saga instance not found: {}", sagaId);
//                return;
//            }
//
//            SagaInstance sagaInstance = sagaInstanceOpt.get();
//
//            // Tính thời gian thực thi
//            long executionTime = 0;
//            if (startTime.get() != null) {
//                executionTime = System.currentTimeMillis() - startTime.get();
//                startTime.remove();
//            }
//
//            // Lấy kết quả action và lỗi (nếu có)
//            Object actionResult = null;
//            Object actionError = null;
//
//            // Tạo history entry
//            try {
//                SagaHistory history = SagaHistory.builder()
//                        .sagaId(sagaId)
//                        .sagaType(sagaInstance.getSagaType())
//                        .sourceState(source.getId().name())
//                        .targetState(target.getId().name())
//                        .event(event != null ? event.name() : "INTERNAL")
//                        .actionName(getActionName(transition))
//                        .actionResult(actionResult != null ? objectMapper.writeValueAsString(actionResult) : null)
//                        .errorMessage(actionError != null ? actionError.toString() : null)
//                        .executionTimeMs(executionTime)
//                        .instanceId(INSTANCE_ID)
//                        .isCompensation(isCompensationTransition(source.getId().name(), target.getId().name()))
//                        .createdAt(LocalDateTime.now())
//                        .build();
//
//                historyRepository.save(history);
//                log.debug("Saved saga history: {}", history);
//            } catch (Exception e) {
//                log.error("Error saving saga history", e);
//            }
//
//            // Thêm checkpoint vào saga instance
//            try {
//                sagaInstance.addCheckpoint(
//                        target.getId().name(),
//                        event != null ? event.name() : "INTERNAL",
//                        actionResult != null ? objectMapper.writeValueAsString(actionResult) : null
//                );
//            } catch (Exception e) {
//                log.error("Error adding checkpoint", e);
//            }
//
//            // Cập nhật trạng thái saga instance
//            sagaInstance.setCurrentState(target.getId().name());
//            sagaInstance.setUpdatedAt(LocalDateTime.now());
//            instanceRepository.save(sagaInstance);
//
//            log.info("Recorded transition for saga {}: {} -> {} by event {} in {}ms",
//                    sagaId, source.getId(), target.getId(), event, executionTime);
//
//        } catch (Exception e) {
//            log.error("Error recording saga transition", e);
//        }
//    }
//
//    /**
//     * Tìm saga ID từ lịch sử gần đây nhất dựa trên source state và target state
//     */
//    private String findSagaIdFromRecentHistory(String sourceState, String targetState) {
//        try {
//            // Tìm lịch sử saga gần đây nhất có cùng source và target state
//            List<SagaHistory> histories = historyRepository.findBySourceStateAndTargetStateOrderByCreatedAtDesc(
//                    sourceState, targetState);
//
//            if (!histories.isEmpty()) {
//                return histories.get(0).getSagaId();
//            }
//
//            // Nếu không tìm thấy, thử tìm bất kỳ saga nào có trạng thái hiện tại là source state
//            List<SagaInstance> instances = instanceRepository.findByCurrentState(sourceState);
//            if (!instances.isEmpty()) {
//                return instances.get(0).getId();
//            }
//        } catch (Exception e) {
//            log.error("Error finding saga ID from history", e);
//        }
//        return null;
//    }
//
//    private String getActionName(Transition<S, E> transition) {
//        try {
//            if (transition.getActions() != null && !transition.getActions().isEmpty()) {
//                return transition.getActions().iterator().next().getClass().getSimpleName();
//            }
//        } catch (Exception e) {
//            log.debug("Could not get action name", e);
//        }
//        return null;
//    }
//
//    private boolean isCompensationTransition(String sourceState, String targetState) {
//        return targetState.startsWith("COMPENSATING_") ||
//               sourceState.startsWith("COMPENSATING_");
//    }
//}
