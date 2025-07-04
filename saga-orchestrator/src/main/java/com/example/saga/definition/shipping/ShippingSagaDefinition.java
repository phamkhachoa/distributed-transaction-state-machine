package com.example.saga.definition.shipping;

import com.example.saga.action.ShippingAction;
import com.example.saga.model.ShippingEvents;
import com.example.saga.model.ShippingStates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
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
 * State machine configuration for the shipping saga
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableStateMachineFactory(name = "shippingSagaStateMachineFactory")
public class ShippingSagaDefinition extends EnumStateMachineConfigurerAdapter<ShippingStates, ShippingEvents> {

    private final ShippingAction shippingAction;
    
    @Override
    public void configure(StateMachineStateConfigurer<ShippingStates, ShippingEvents> states) throws Exception {
        states
            .withStates()
                .initial(ShippingStates.SHIPPING_CREATED)
                .states(EnumSet.allOf(ShippingStates.class))
                .end(ShippingStates.SHIPPING_COMPLETED)
                .end(ShippingStates.SHIPPING_CANCELLED)
                .end(ShippingStates.SHIPPING_FAILED);
    }
    
    @Override
    public void configure(StateMachineTransitionConfigurer<ShippingStates, ShippingEvents> transitions) 
            throws Exception {
        transitions
            // Initial transition
            .withExternal()
                .source(ShippingStates.SHIPPING_CREATED)
                .target(ShippingStates.SELLER_PREPARING)
                .event(ShippingEvents.START_SHIPPING)
                .action(createShippingAction())
                .and()
            
            // Seller preparation
            .withExternal()
                .source(ShippingStates.SELLER_PREPARING)
                .target(ShippingStates.SELLER_PREPARED)
                .event(ShippingEvents.SELLER_COMPLETED_PREPARING)
                .and()
                
            // Pickup scheduling
            .withExternal()
                .source(ShippingStates.SELLER_PREPARED)
                .target(ShippingStates.PICKUP_SCHEDULED)
                .event(ShippingEvents.PICKUP_REQUESTED)
                .and()
                
            // Pickup
            .withExternal()
                .source(ShippingStates.PICKUP_SCHEDULED)
                .target(ShippingStates.PICKED_UP)
                .event(ShippingEvents.PICKUP_COMPLETED)
                .and()
                
            // Sorting center
            .withExternal()
                .source(ShippingStates.PICKED_UP)
                .target(ShippingStates.SORTING_CENTER_RECEIVED)
                .event(ShippingEvents.ARRIVED_AT_SORTING_CENTER)
                .and()
            .withExternal()
                .source(ShippingStates.SORTING_CENTER_RECEIVED)
                .target(ShippingStates.SORTING_CENTER_PROCESSING)
                .event(ShippingEvents.START_SORTING)
                .and()
            .withExternal()
                .source(ShippingStates.SORTING_CENTER_PROCESSING)
                .target(ShippingStates.SORTING_CENTER_COMPLETED)
                .event(ShippingEvents.SORTING_COMPLETED)
                .and()
                
            // Local warehouse
            .withExternal()
                .source(ShippingStates.SORTING_CENTER_COMPLETED)
                .target(ShippingStates.LOCAL_WAREHOUSE_RECEIVED)
                .event(ShippingEvents.ARRIVED_AT_LOCAL_WAREHOUSE)
                .and()
            .withExternal()
                .source(ShippingStates.LOCAL_WAREHOUSE_RECEIVED)
                .target(ShippingStates.LOCAL_WAREHOUSE_PROCESSING)
                .event(ShippingEvents.START_LOCAL_PROCESSING)
                .and()
            .withExternal()
                .source(ShippingStates.LOCAL_WAREHOUSE_PROCESSING)
                .target(ShippingStates.LOCAL_WAREHOUSE_READY)
                .event(ShippingEvents.READY_FOR_DELIVERY)
                .and()
                
            // Delivery
            .withExternal()
                .source(ShippingStates.LOCAL_WAREHOUSE_READY)
                .target(ShippingStates.OUT_FOR_DELIVERY)
                .event(ShippingEvents.START_DELIVERY)
                .and()
            .withExternal()
                .source(ShippingStates.OUT_FOR_DELIVERY)
                .target(ShippingStates.DELIVERED)
                .event(ShippingEvents.DELIVERY_COMPLETED)
                .and()
            .withExternal()
                .source(ShippingStates.OUT_FOR_DELIVERY)
                .target(ShippingStates.DELIVERY_FAILED)
                .event(ShippingEvents.DELIVERY_ATTEMPT_FAILED)
                .and()
                
            // Completion
            .withExternal()
                .source(ShippingStates.DELIVERED)
                .target(ShippingStates.SHIPPING_COMPLETED)
                .event(ShippingEvents.COMPLETE_SHIPPING)
                .and()
                
            // Return handling
            .withExternal()
                .source(ShippingStates.DELIVERED)
                .target(ShippingStates.RETURNING_TO_SELLER)
                .event(ShippingEvents.START_RETURN)
                .and()
            .withExternal()
                .source(ShippingStates.RETURNING_TO_SELLER)
                .target(ShippingStates.RETURNED_TO_SELLER)
                .event(ShippingEvents.RETURN_COMPLETED)
                .and()
            .withExternal()
                .source(ShippingStates.RETURNED_TO_SELLER)
                .target(ShippingStates.SHIPPING_COMPLETED)
                .event(ShippingEvents.COMPLETE_SHIPPING)
                .and()
                
            // Cancellation
            .withExternal()
                .source(ShippingStates.SELLER_PREPARING)
                .target(ShippingStates.SHIPPING_CANCELLED)
                .event(ShippingEvents.CANCEL_SHIPPING)
                .and()
            .withExternal()
                .source(ShippingStates.PICKUP_SCHEDULED)
                .target(ShippingStates.SHIPPING_CANCELLED)
                .event(ShippingEvents.CANCEL_SHIPPING)
                .and()
            .withExternal()
                .source(ShippingStates.SORTING_CENTER_PROCESSING)
                .target(ShippingStates.SHIPPING_CANCELLED)
                .event(ShippingEvents.CANCEL_SHIPPING)
                .and()
            .withExternal()
                .source(ShippingStates.LOCAL_WAREHOUSE_PROCESSING)
                .target(ShippingStates.SHIPPING_CANCELLED)
                .event(ShippingEvents.CANCEL_SHIPPING)
                .and()
                
            // Timeouts
            .withExternal()
                .source(ShippingStates.SELLER_PREPARING)
                .target(ShippingStates.SHIPPING_FAILED)
                .event(ShippingEvents.SELLER_PREPARATION_TIMEOUT)
                .and()
            .withExternal()
                .source(ShippingStates.PICKUP_SCHEDULED)
                .target(ShippingStates.SHIPPING_FAILED)
                .event(ShippingEvents.PICKUP_TIMEOUT)
                .and()
            .withExternal()
                .source(ShippingStates.SORTING_CENTER_PROCESSING)
                .target(ShippingStates.SHIPPING_FAILED)
                .event(ShippingEvents.SORTING_TIMEOUT)
                .and()
            .withExternal()
                .source(ShippingStates.LOCAL_WAREHOUSE_PROCESSING)
                .target(ShippingStates.SHIPPING_FAILED)
                .event(ShippingEvents.LOCAL_PROCESSING_TIMEOUT);
    }
    
    @Override
    public void configure(StateMachineConfigurationConfigurer<ShippingStates, ShippingEvents> config) 
            throws Exception {
        config
            .withConfiguration()
                .autoStartup(true)
                .listener(loggingListener());
    }
    
    @Bean
    public StateMachineListener<ShippingStates, ShippingEvents> loggingListener() {
        return new StateMachineListenerAdapter<ShippingStates, ShippingEvents>() {
            @Override
            public void stateChanged(State<ShippingStates, ShippingEvents> from, 
                    State<ShippingStates, ShippingEvents> to) {
                log.info("Shipping saga state changed from {} to {}", 
                        from == null ? "null" : from.getId(), to.getId());
            }
        };
    }
    
    private Action<ShippingStates, ShippingEvents> createShippingAction() {
        return new Action<ShippingStates, ShippingEvents>() {
            @Override
            public void execute(StateContext<ShippingStates, ShippingEvents> context) {
                // This is a wrapper action that delegates to the ShippingAction bean
                // but ensures the correct type signature for the state machine
                try {
                    // We don't need to do anything here since ShippingAction is only used
                    // by the ShippingSagaListener to notify the order saga
                    log.debug("Creating shipping for saga: {}", context.getStateMachine().getId());
                } catch (Exception e) {
                    log.error("Error in shipping action", e);
                }
            }
        };
    }
} 
