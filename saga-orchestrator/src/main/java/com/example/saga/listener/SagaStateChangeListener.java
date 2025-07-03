package com.example.saga.listener;

import com.example.saga.persistence.SagaHistory;
import com.example.saga.persistence.SagaHistoryRepository;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaStateChangeListener<S extends Enum<S>, E extends Enum<E>> 
        extends StateMachineListenerAdapter<S, E> {

    private final SagaHistoryRepository historyRepository;
    private final SagaInstanceRepository instanceRepository;
    private final ObjectMapper objectMapper;
    private static final String INSTANCE_ID = System.getenv().getOrDefault("HOSTNAME", "unknown");
    
    private ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Override
    public void stateContext(StateContext<S, E> stateContext) {
        if (stateContext.getStage() == StateContext.Stage.STATE_ENTRY) {
            startTime.set(System.currentTimeMillis());
        }
    }

    @Override
    @Transactional
    public void transition(Transition<S, E> transition) {
        if (transition == null || transition.getSource() == null || transition.getTarget() == null) {
            return;
        }

        try {
            State<S, E> source = transition.getSource();
            State<S, E> target = transition.getTarget();
            E event = transition.getTrigger().getEvent();
            String sagaId = transition.getStateMachine().getId();
            
            // Get saga instance
            SagaInstance sagaInstance = instanceRepository.findById(sagaId)
                    .orElseThrow(() -> new RuntimeException("Saga instance not found: " + sagaId));
            
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime.get();
            startTime.remove();
            
            // Get action result and error if any
            Object actionResult = transition.getStateMachine()
                    .getExtendedState().getVariables().get("actionResult");
            Object actionError = transition.getStateMachine()
                    .getExtendedState().getVariables().get("actionError");
            
            // Create history entry
            SagaHistory history = SagaHistory.builder()
                    .sagaId(sagaId)
                    .sagaType(sagaInstance.getSagaType())
                    .sourceState(source.getId().name())
                    .targetState(target.getId().name())
                    .event(event != null ? event.name() : "INTERNAL")
                    .actionName(getActionName(transition))
                    .actionResult(actionResult != null ? objectMapper.writeValueAsString(actionResult) : null)
                    .errorMessage(actionError != null ? actionError.toString() : null)
                    .executionTimeMs(executionTime)
                    .instanceId(INSTANCE_ID)
                    .isCompensation(isCompensationTransition(source.getId().name(), target.getId().name()))
                    .build();
            
            historyRepository.save(history);
            
            // Add checkpoint to saga instance
            sagaInstance.addCheckpoint(
                target.getId().name(),
                event != null ? event.name() : "INTERNAL",
                actionResult != null ? objectMapper.writeValueAsString(actionResult) : null
            );
            
            // Update saga instance
            sagaInstance.setCurrentState(target.getId().name());
            instanceRepository.save(sagaInstance);
            
            log.info("Recorded transition for saga {}: {} -> {} by event {} in {}ms", 
                    sagaId, source.getId(), target.getId(), event, executionTime);
            
        } catch (Exception e) {
            log.error("Error recording saga transition", e);
        }
    }
    
    private String getActionName(Transition<S, E> transition) {
        if (transition.getTrigger() != null && 
            transition.getTrigger().getAction() != null) {
            return transition.getTrigger().getAction().getClass().getSimpleName();
        }
        return null;
    }
    
    private boolean isCompensationTransition(String sourceState, String targetState) {
        return targetState.startsWith("COMPENSATING_") || 
               sourceState.startsWith("COMPENSATING_");
    }
} 