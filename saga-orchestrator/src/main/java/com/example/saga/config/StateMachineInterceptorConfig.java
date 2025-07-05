package com.example.saga.config;

import com.example.saga.interceptor.SagaPersistenceInterceptor;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class để đăng ký SagaPersistenceInterceptor với state machine
 */
@Configuration
@RequiredArgsConstructor
public class StateMachineInterceptorConfig {

    private final StateMachineFactory<SagaStates, SagaEvents> stateMachineFactory;
    private final SagaPersistenceInterceptor<SagaStates, SagaEvents> sagaPersistenceInterceptor;

    /**
     * Đăng ký interceptor với state machine factory
     */
    @PostConstruct
    public void init() {
        stateMachineFactory.getStateMachine().getStateMachineAccessor()
                .doWithAllRegions(accessor -> accessor.addStateMachineInterceptor(sagaPersistenceInterceptor));
    }
} 