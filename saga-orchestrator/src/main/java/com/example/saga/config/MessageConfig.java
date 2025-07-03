package com.example.saga.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for RabbitMQ messaging
 */
@Configuration
public class MessageConfig {
    
    // Exchange names
    public static final String SAGA_COMMAND_EXCHANGE = "saga-command-exchange";
    public static final String SAGA_REPLY_EXCHANGE = "saga-reply-exchange";
    
    // Queue names
    public static final String SAGA_START_QUEUE = "saga-start-queue";
    public static final String PAYMENT_COMMAND_QUEUE = "payment-command-queue";
    public static final String PAYMENT_REPLY_QUEUE = "payment-reply-queue";
    public static final String INVENTORY_COMMAND_QUEUE = "inventory-command-queue";
    public static final String INVENTORY_REPLY_QUEUE = "inventory-reply-queue";
    public static final String SHIPPING_COMMAND_QUEUE = "shipping-command-queue";
    public static final String SHIPPING_REPLY_QUEUE = "shipping-reply-queue";
    
    // Routing keys for commands
    public static final String PAYMENT_CHARGE_KEY = "payment.charge";
    public static final String PAYMENT_PROCESS_KEY = "payment.process";
    public static final String PAYMENT_REFUND_KEY = "payment.refund";
    public static final String INVENTORY_RESERVE_KEY = "inventory.reserve";
    public static final String INVENTORY_RELEASE_KEY = "inventory.release";
    public static final String SHIPPING_SCHEDULE_KEY = "shipping.schedule";
    public static final String SHIPPING_CANCEL_KEY = "shipping.cancel";
    public static final String SAGA_START_KEY = "saga.start";
    
    // Routing keys for replies
    public static final String PAYMENT_REPLY_KEY = "payment.reply";
    public static final String INVENTORY_REPLY_KEY = "inventory.reply";
    public static final String SHIPPING_REPLY_KEY = "shipping.reply";
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
    
    @Bean
    public DirectExchange sagaCommandExchange() {
        return new DirectExchange(SAGA_COMMAND_EXCHANGE);
    }
    
    @Bean
    public DirectExchange sagaReplyExchange() {
        return new DirectExchange(SAGA_REPLY_EXCHANGE);
    }
    
    @Bean
    public Queue sagaStartQueue() {
        return new Queue(SAGA_START_QUEUE);
    }
    
    @Bean
    public Queue paymentCommandQueue() {
        return new Queue(PAYMENT_COMMAND_QUEUE);
    }
    
    @Bean
    public Queue paymentReplyQueue() {
        return new Queue(PAYMENT_REPLY_QUEUE);
    }
    
    @Bean
    public Queue inventoryCommandQueue() {
        return new Queue(INVENTORY_COMMAND_QUEUE);
    }
    
    @Bean
    public Queue inventoryReplyQueue() {
        return new Queue(INVENTORY_REPLY_QUEUE);
    }
    
    @Bean
    public Queue shippingCommandQueue() {
        return new Queue(SHIPPING_COMMAND_QUEUE);
    }
    
    @Bean
    public Queue shippingReplyQueue() {
        return new Queue(SHIPPING_REPLY_QUEUE);
    }
    
    @Bean
    public Binding paymentChargeBinding() {
        return BindingBuilder.bind(paymentCommandQueue())
                .to(sagaCommandExchange())
                .with(PAYMENT_CHARGE_KEY);
    }
    
    @Bean
    public Binding paymentProcessBinding() {
        return BindingBuilder.bind(paymentCommandQueue())
                .to(sagaCommandExchange())
                .with(PAYMENT_PROCESS_KEY);
    }
    
    @Bean
    public Binding paymentRefundBinding() {
        return BindingBuilder.bind(paymentCommandQueue())
                .to(sagaCommandExchange())
                .with(PAYMENT_REFUND_KEY);
    }
    
    @Bean
    public Binding inventoryReserveBinding() {
        return BindingBuilder.bind(inventoryCommandQueue())
                .to(sagaCommandExchange())
                .with(INVENTORY_RESERVE_KEY);
    }
    
    @Bean
    public Binding inventoryReleaseBinding() {
        return BindingBuilder.bind(inventoryCommandQueue())
                .to(sagaCommandExchange())
                .with(INVENTORY_RELEASE_KEY);
    }
    
    @Bean
    public Binding shippingScheduleBinding() {
        return BindingBuilder.bind(shippingCommandQueue())
                .to(sagaCommandExchange())
                .with(SHIPPING_SCHEDULE_KEY);
    }
    
    @Bean
    public Binding shippingCancelBinding() {
        return BindingBuilder.bind(shippingCommandQueue())
                .to(sagaCommandExchange())
                .with(SHIPPING_CANCEL_KEY);
    }
    
    @Bean
    public Binding sagaStartBinding() {
        return BindingBuilder.bind(sagaStartQueue())
                .to(sagaCommandExchange())
                .with(SAGA_START_KEY);
    }
    
    @Bean
    public Binding paymentReplyBinding() {
        return BindingBuilder.bind(paymentReplyQueue())
                .to(sagaReplyExchange())
                .with(PAYMENT_REPLY_KEY);
    }
    
    @Bean
    public Binding inventoryReplyBinding() {
        return BindingBuilder.bind(inventoryReplyQueue())
                .to(sagaReplyExchange())
                .with(INVENTORY_REPLY_KEY);
    }
    
    @Bean
    public Binding shippingReplyBinding() {
        return BindingBuilder.bind(shippingReplyQueue())
                .to(sagaReplyExchange())
                .with(SHIPPING_REPLY_KEY);
    }
} 