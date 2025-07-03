package com.example.saga.listener;

import com.example.saga.action.ShippingAction;
import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.ShippingContext;
import com.example.saga.model.ShippingEvents;
import com.example.saga.model.ShippingStates;
import com.example.saga.persistence.SagaInstance;
import com.example.saga.persistence.SagaInstanceRepository;
import com.example.saga.service.SagaOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.statemachine.event.OnStateChangedEvent;

import java.util.List;

/**
 * Listener for shipping saga events
 * Monitors shipping saga state changes and notifies the order saga when shipping completes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShippingSagaListener {

    private final SagaOrchestrationService orchestrationService;
    private final ShippingAction shippingAction;
    private final SagaInstanceRepository sagaInstanceRepository;
    
    /**
     * Listen for state change events in shipping sagas
     */
    @EventListener
    public void handleShippingStateChange(OnStateChangedEvent event) {
        try {
            // Get the state machine from the event
            StateMachine<ShippingStates, ShippingEvents> stateMachine = 
                    (StateMachine<ShippingStates, ShippingEvents>) event.getSource();
            
            // Get the state from the state machine
            State<ShippingStates, ShippingEvents> state = stateMachine.getState();
            
            log.debug("Shipping saga state changed: {}", state.getId());
            
            // Get the shipping context from the state machine
            ShippingContext shippingContext = (ShippingContext) stateMachine.getExtendedState()
                    .getVariables().get("shippingContext");
            
            if (shippingContext == null) {
                log.warn("No shipping context found in state machine");
                return;
            }
            
            log.debug("Processing shipping state change for order: {}", shippingContext.getOrderId());
            
            // Handle shipping completion
            if (state.getId() == ShippingStates.SHIPPING_COMPLETED) {
                handleShippingCompletion(shippingContext, true);
            }
            
            // Handle shipping failure
            if (state.getId() == ShippingStates.SHIPPING_FAILED) {
                handleShippingCompletion(shippingContext, false);
            }
        } catch (Exception e) {
            log.error("Error handling shipping state change", e);
        }
    }
    
    /**
     * Handle shipping completion by notifying the order saga
     */
    private void handleShippingCompletion(ShippingContext shippingContext, boolean success) {
        try {
            // Find the order saga that started this shipping saga
            List<SagaInstance> orderSagas = sagaInstanceRepository.findBySagaType("ORDER_SAGA");
            if (orderSagas.isEmpty()) {
                log.warn("No order saga found for shipping context");
                return;
            }
            
            // Find the saga that has this shipping saga ID
            SagaInstance orderSaga = null;
            for (SagaInstance saga : orderSagas) {
                // Get the saga context
                SagaContext sagaContext = (SagaContext) orchestrationService.getSagaContext(saga.getId());
                
                // Check if this is the parent saga
                if (shippingContext.getSagaId().equals(sagaContext.getPayload().get("shippingSagaId"))) {
                    orderSaga = saga;
                    break;
                }
            }
            
            if (orderSaga == null) {
                log.warn("No matching order saga found for shipping saga: {}", shippingContext.getSagaId());
                return;
            }
            
            // Notify the shipping action to update the order saga
            shippingAction.handleShippingCompletion(orderSaga.getId(), shippingContext.getSagaId(), success);
            
            log.info("Notified order saga {} about shipping completion (success={})", 
                    orderSaga.getId(), success);
        } catch (Exception e) {
            log.error("Error handling shipping completion", e);
        }
    }
    
    /**
     * Handle shipping cancellation events
     */
    @EventListener
    public void handleShippingCancellation(OnStateChangedEvent event) {
        try {
            // Get the state machine from the event
            StateMachine<ShippingStates, ShippingEvents> stateMachine = 
                    (StateMachine<ShippingStates, ShippingEvents>) event.getSource();
            
            // Get the state from the state machine
            State<ShippingStates, ShippingEvents> state = stateMachine.getState();
            
            if (state.getId() == ShippingStates.SHIPPING_CANCELLED) {
                // Get the shipping context from the state machine
                ShippingContext shippingContext = (ShippingContext) stateMachine.getExtendedState()
                        .getVariables().get("shippingContext");
                
                if (shippingContext == null) {
                    log.warn("No shipping context found in state machine for cancellation");
                    return;
                }
                
                log.info("Shipping cancelled for saga: {}", shippingContext.getSagaId());
            }
        } catch (Exception e) {
            log.error("Error handling shipping cancellation", e);
        }
    }
    
    /**
     * Handle shipping timeout events
     */
    @EventListener
    public void handleShippingTimeout(OnStateChangedEvent event) {
        try {
            // Get the state machine from the event
            StateMachine<ShippingStates, ShippingEvents> stateMachine = 
                    (StateMachine<ShippingStates, ShippingEvents>) event.getSource();
            
            // Get the state from the state machine
            State<ShippingStates, ShippingEvents> state = stateMachine.getState();
            
            if (state.getId() == ShippingStates.SHIPPING_FAILED) {
                // Get the shipping context from the state machine
                ShippingContext shippingContext = (ShippingContext) stateMachine.getExtendedState()
                        .getVariables().get("shippingContext");
                
                if (shippingContext == null) {
                    log.warn("No shipping context found in state machine for timeout");
                    return;
                }
                
                log.info("Shipping timed out for saga: {}", shippingContext.getSagaId());
            }
        } catch (Exception e) {
            log.error("Error handling shipping timeout", e);
        }
    }
} 