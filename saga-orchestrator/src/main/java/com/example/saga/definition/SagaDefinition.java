package com.example.saga.definition;

import org.springframework.statemachine.config.StateMachineFactory;

/**
 * Base interface for all saga definitions
 */
public interface SagaDefinition<S extends Enum<S>, E extends Enum<E>> {

    public static final String TARGET_ID_HEADER = "TARGET_ID_HEADER";
    
    /**
     * Get the state machine factory for this saga type
     */
    StateMachineFactory<S, E> getStateMachineFactory();
    
    /**
     * Get the initial state for this saga type
     */
    S getInitialState();
    
    /**
     * Get the saga type identifier
     */
    String getSagaType();
    
    /**
     * Validate the saga context for this saga type
     */
    void validateContext(Object context);
    
    /**
     * Get timeout duration in minutes for this saga type
     */
    long getTimeoutMinutes();
} 
