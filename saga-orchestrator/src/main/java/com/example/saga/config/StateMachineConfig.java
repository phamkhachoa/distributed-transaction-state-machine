package com.example.saga.config;

import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;

/**
 * Main configuration for the Order Saga State Machine
 */
@Slf4j
@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
public class StateMachineConfig extends EnumStateMachineConfigurerAdapter<SagaStates, SagaEvents> {
    
    @Override
    public void configure(StateMachineConfigurationConfigurer<SagaStates, SagaEvents> config) throws Exception {
        config
            .withConfiguration()
                .autoStartup(true)
                .listener(listener());
    }
    
    @Override
    public void configure(StateMachineStateConfigurer<SagaStates, SagaEvents> states) throws Exception {
        states
            .withStates()
                .initial(SagaStates.ORDER_CREATED)
                .states(EnumSet.allOf(SagaStates.class))
                .end(SagaStates.ORDER_COMPLETED)
                .end(SagaStates.ORDER_CANCELLED)
                .end(SagaStates.COMPENSATION_COMPLETED);
    }
    
    @Override
    public void configure(StateMachineTransitionConfigurer<SagaStates, SagaEvents> transitions) throws Exception {
        transitions
            // Main flow transitions
            .withExternal()
                .source(SagaStates.ORDER_CREATED).target(SagaStates.PAYMENT_PROCESSING)
                .event(SagaEvents.START_SAGA)
                .and()
            .withExternal()
                .source(SagaStates.PAYMENT_PROCESSING).target(SagaStates.PAYMENT_COMPLETED)
                .event(SagaEvents.PAYMENT_SUCCESS)
                .and()
            .withExternal()
                .source(SagaStates.PAYMENT_COMPLETED).target(SagaStates.INVENTORY_RESERVING)
                .and()
            .withExternal()
                .source(SagaStates.INVENTORY_RESERVING).target(SagaStates.INVENTORY_RESERVED)
                .event(SagaEvents.INVENTORY_RESERVED)
                .and()
            .withExternal()
                .source(SagaStates.INVENTORY_RESERVED).target(SagaStates.SHIPPING_SCHEDULING)
                .and()
            .withExternal()
                .source(SagaStates.SHIPPING_SCHEDULING).target(SagaStates.SHIPPING_SCHEDULED)
                .event(SagaEvents.SHIPPING_SCHEDULED)
                .and()
            .withExternal()
                .source(SagaStates.SHIPPING_SCHEDULED).target(SagaStates.ORDER_COMPLETED)
                .and()
                
            // Compensation flow - Payment failed
            .withExternal()
                .source(SagaStates.PAYMENT_PROCESSING).target(SagaStates.ORDER_CANCELLED)
                .event(SagaEvents.PAYMENT_FAILED)
                .and()
                
            // Compensation flow - Inventory insufficient
            .withExternal()
                .source(SagaStates.INVENTORY_RESERVING).target(SagaStates.COMPENSATING_PAYMENT)
                .event(SagaEvents.INVENTORY_INSUFFICIENT)
                .and()
            .withExternal()
                .source(SagaStates.COMPENSATING_PAYMENT).target(SagaStates.COMPENSATION_COMPLETED)
                .event(SagaEvents.PAYMENT_COMPENSATED)
                .and()
                
            // Compensation flow - Shipping failed
            .withExternal()
                .source(SagaStates.SHIPPING_SCHEDULING).target(SagaStates.COMPENSATING_INVENTORY)
                .event(SagaEvents.SHIPPING_FAILED)
                .and()
            .withExternal()
                .source(SagaStates.COMPENSATING_INVENTORY).target(SagaStates.COMPENSATING_PAYMENT)
                .event(SagaEvents.INVENTORY_COMPENSATED)
                .and()
                
            // Timeout transitions
            .withExternal()
                .source(SagaStates.PAYMENT_PROCESSING).target(SagaStates.ORDER_CANCELLED)
                .event(SagaEvents.PAYMENT_TIMEOUT)
                .and()
            .withExternal()
                .source(SagaStates.INVENTORY_RESERVING).target(SagaStates.COMPENSATING_PAYMENT)
                .event(SagaEvents.INVENTORY_TIMEOUT)
                .and()
            .withExternal()
                .source(SagaStates.SHIPPING_SCHEDULING).target(SagaStates.COMPENSATING_INVENTORY)
                .event(SagaEvents.SHIPPING_TIMEOUT);
    }
    
    private StateMachineListener<SagaStates, SagaEvents> listener() {
        return new StateMachineListenerAdapter<SagaStates, SagaEvents>() {
            @Override
            public void stateChanged(State<SagaStates, SagaEvents> from, State<SagaStates, SagaEvents> to) {
                if (from != null) {
                    log.info("State changed from {} to {}", from.getId(), to.getId());
                } else {
                    log.info("State machine initialized to {}", to.getId());
                }
            }
        };
    }
} 