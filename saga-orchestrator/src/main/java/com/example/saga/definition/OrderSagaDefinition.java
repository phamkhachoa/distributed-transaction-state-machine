package com.example.saga.definition;

import com.example.saga.model.SagaContext;
import com.example.saga.model.SagaEvents;
import com.example.saga.model.SagaStates;
import com.example.saga.action.PaymentAction;
import com.example.saga.action.InventoryAction;
import com.example.saga.action.ShippingAction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.config.configurers.StateConfigurer;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component("orderSagaDefinition")
@RequiredArgsConstructor
public class OrderSagaDefinition implements SagaDefinition<SagaStates, SagaEvents> {

    private final PaymentAction paymentAction;
    private final InventoryAction inventoryAction;
    private final ShippingAction shippingAction;
    private final StateMachineFactory<SagaStates, SagaEvents> orderStateMachineFactory;

    @Override
    public StateMachineFactory<SagaStates, SagaEvents> getStateMachineFactory() {
        return orderStateMachineFactory;
    }

    @Override
    public SagaStates getInitialState() {
        return SagaStates.ORDER_CREATED;
    }

    @Override
    public String getSagaType() {
        return "ORDER_SAGA";
    }

    @Override
    public void validateContext(Object context) {
        if (!(context instanceof SagaContext)) {
            throw new IllegalArgumentException("Context must be of type SagaContext");
        }
        SagaContext sagaContext = (SagaContext) context;
        if (sagaContext.getOrderId() == null || sagaContext.getUserId() == null || 
            sagaContext.getAmount() == null || sagaContext.getProducts() == null) {
            throw new IllegalArgumentException("Missing required fields in saga context");
        }
    }

    @Override
    public long getTimeoutMinutes() {
        return 30; // Default timeout for order saga
    }

    @Bean
    public void configure(StateMachineStateConfigurer<SagaStates, SagaEvents> states) throws Exception {
        states
            .withStates()
                .initial(SagaStates.ORDER_CREATED)
                .states(EnumSet.allOf(SagaStates.class))
                .end(SagaStates.ORDER_COMPLETED)
                .end(SagaStates.ORDER_CANCELLED)
                .end(SagaStates.COMPENSATION_COMPLETED);
    }

    @Bean
    public void configure(StateMachineTransitionConfigurer<SagaStates, SagaEvents> transitions) throws Exception {
        transitions
            // Main flow transitions
            .withExternal()
                .source(SagaStates.ORDER_CREATED)
                .target(SagaStates.PAYMENT_PROCESSING)
                .event(SagaEvents.START_SAGA)
                .action(paymentAction)
                .and()
            .withExternal()
                .source(SagaStates.PAYMENT_PROCESSING)
                .target(SagaStates.PAYMENT_COMPLETED)
                .event(SagaEvents.PAYMENT_SUCCESS)
                .action(context -> {
                    // Trigger next step automatically
                    context.getStateMachine().sendEvent(SagaEvents.START_INVENTORY);
                })
                .and()
            .withExternal()
                .source(SagaStates.PAYMENT_COMPLETED)
                .target(SagaStates.INVENTORY_RESERVING)
                .event(SagaEvents.START_INVENTORY)
                .action(inventoryAction)
                .and()
            .withExternal()
                .source(SagaStates.INVENTORY_RESERVING)
                .target(SagaStates.INVENTORY_RESERVED)
                .event(SagaEvents.INVENTORY_RESERVED)
                .action(context -> {
                    // Trigger next step automatically
                    context.getStateMachine().sendEvent(SagaEvents.START_SHIPPING);
                })
                .and()
            .withExternal()
                .source(SagaStates.INVENTORY_RESERVED)
                .target(SagaStates.SHIPPING_SCHEDULING)
                .event(SagaEvents.START_SHIPPING)
                .action(shippingAction)
                .and()
            .withExternal()
                .source(SagaStates.SHIPPING_SCHEDULING)
                .target(SagaStates.SHIPPING_SCHEDULED)
                .event(SagaEvents.SHIPPING_SCHEDULED)
                .action(context -> {
                    // Complete the saga
                    context.getStateMachine().sendEvent(SagaEvents.COMPLETE_SAGA);
                })
                .and()
            .withExternal()
                .source(SagaStates.SHIPPING_SCHEDULED)
                .target(SagaStates.ORDER_COMPLETED)
                .event(SagaEvents.COMPLETE_SAGA)
                .and()
                
            // Compensation flow - Payment failed
            .withExternal()
                .source(SagaStates.PAYMENT_PROCESSING)
                .target(SagaStates.ORDER_CANCELLED)
                .event(SagaEvents.PAYMENT_FAILED)
                .and()
                
            // Compensation flow - Inventory insufficient
            .withExternal()
                .source(SagaStates.INVENTORY_RESERVING)
                .target(SagaStates.COMPENSATING_PAYMENT)
                .event(SagaEvents.INVENTORY_INSUFFICIENT)
                .action(paymentAction)
                .and()
            .withExternal()
                .source(SagaStates.COMPENSATING_PAYMENT)
                .target(SagaStates.COMPENSATION_COMPLETED)
                .event(SagaEvents.PAYMENT_COMPENSATED)
                .and()
                
            // Compensation flow - Shipping failed
            .withExternal()
                .source(SagaStates.SHIPPING_SCHEDULING)
                .target(SagaStates.COMPENSATING_INVENTORY)
                .event(SagaEvents.SHIPPING_FAILED)
                .action(inventoryAction)
                .and()
            .withExternal()
                .source(SagaStates.COMPENSATING_INVENTORY)
                .target(SagaStates.COMPENSATING_PAYMENT)
                .event(SagaEvents.INVENTORY_COMPENSATED)
                .action(paymentAction)
                .and()
                
            // Timeout transitions
            .withExternal()
                .source(SagaStates.PAYMENT_PROCESSING)
                .target(SagaStates.ORDER_CANCELLED)
                .event(SagaEvents.PAYMENT_TIMEOUT)
                .and()
            .withExternal()
                .source(SagaStates.INVENTORY_RESERVING)
                .target(SagaStates.COMPENSATING_PAYMENT)
                .event(SagaEvents.INVENTORY_TIMEOUT)
                .action(paymentAction)
                .and()
            .withExternal()
                .source(SagaStates.SHIPPING_SCHEDULING)
                .target(SagaStates.COMPENSATING_INVENTORY)
                .event(SagaEvents.SHIPPING_TIMEOUT)
                .action(inventoryAction);
    }

    @Bean
    public void configure(StateMachineConfigurationConfigurer<SagaStates, SagaEvents> config) throws Exception {
        config
            .withConfiguration()
                .autoStartup(true);
    }
} 