package com.example.saga.listener;

import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.persistence.SagaHistory;
import com.example.saga.persistence.SagaHistoryRepository;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import com.example.saga.service.SagaOrchestrationService;
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
    private final SagaOrchestrationService orchestrationService;
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
            String sagaId = "unknown"; // fallback saga ID
            
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - (startTime.get() != null ? startTime.get() : 0);
            if (startTime.get() != null) {
                startTime.remove();
            }
            
            // Create history entry with basic information
            SagaHistory history = SagaHistory.builder()
                    .sagaId(sagaId)
                    .sagaType("ORDER_SAGA")
                    .sourceState(source.getId().name())
                    .targetState(target.getId().name())
                    .event(event != null ? event.name() : "INTERNAL")
                    .actionName(getActionName(transition))
                    .executionTimeMs(executionTime)
                    .instanceId(INSTANCE_ID)
                    .isCompensation(isCompensationTransition(source.getId().name(), target.getId().name()))
                    .build();
            
            historyRepository.save(history);
            
            log.info("Recorded transition: {} -> {} by event {} in {}ms", 
                    source.getId(), target.getId(), event, executionTime);
            
        } catch (Exception e) {
            log.error("Error recording saga transition", e);
        }
    }
    
    private String getActionName(Transition<S, E> transition) {
        if (transition.getActions() != null && !transition.getActions().isEmpty()) {
            return transition.getActions().iterator().next().getClass().getSimpleName();
        }
        return null;
    }
    
    private boolean isCompensationTransition(String sourceState, String targetState) {
        return targetState.startsWith("COMPENSATING_") || 
               sourceState.startsWith("COMPENSATING_");
    }


} 