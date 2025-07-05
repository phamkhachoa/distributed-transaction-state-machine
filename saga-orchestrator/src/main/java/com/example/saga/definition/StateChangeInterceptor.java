//package com.example.saga.definition;
//
//import com.example.saga.definition.order.OrderSagaDefinition;
//import com.example.saga.persistence.SagaHistory;
//import com.example.saga.persistence.SagaHistoryRepository;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.messaging.Message;
//import org.springframework.statemachine.StateMachine;
//import org.springframework.statemachine.state.State;
//import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
//import org.springframework.statemachine.transition.Transition;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.Optional;
//import java.util.UUID;
//
///**
// * Created by jt on 11/30/19.
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class StateChangeInterceptor extends StateMachineInterceptorAdapter {
//
//    private final SagaHistoryRepository sagaHistoryRepository;
//
//    @Override
//    @Transactional
//    public void postStateChange(State state, Message message, Transition transition, StateMachine stateMachine, StateMachine rootStateMachine) {
//        log.debug("Post-State Change");
//
//        Optional.ofNullable(message)
//                .flatMap(msg -> Optional.ofNullable((String) msg.getHeaders().getOrDefault(SagaDefinition.TARGET_ID_HEADER, " ")))
//                .ifPresent(targetId -> {
//                    log.debug("Saving post-state change for targetId id: " + targetId + " Status: " + state.getId());
//
//                    SagaHistory sagaHistory = new SagaHistory();
//                    sagaHistory.setSagaId(targetId);
//                    sagaHistory.setSourceState(transition.getSource().getId().toString());
//                    sagaHistory.setTargetState(state.getId().toString());
//                    sagaHistory.setEvent(transition.getTrigger().getEvent().toString());
//
//                    sagaHistoryRepository.save(sagaHistory);
//                });
//    }
//}
