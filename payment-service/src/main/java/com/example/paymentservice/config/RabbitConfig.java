package com.example.paymentservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // Exchanges and Queues from order-service (for reference)
    public static final String SAGA_COMMAND_EXCHANGE = "saga.command.exchange";
    public static final String SAGA_REPLY_EXCHANGE = "saga.reply.exchange";
    public static final String SAGA_REPLY_QUEUE_ROUTING_KEY = "saga.reply.queue";
    
    // Domain Events Exchange
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events.exchange";
    
    // Dead Letter Queue Configuration
    public static final String PAYMENT_DLX = "payment.dlx";
    public static final String PAYMENT_DLQ = "payment.dlq";

    // Payment-specific queue
    public static final String PAYMENT_COMMAND_QUEUE = "payment.command.queue";
    // Routing keys
    public static final String PAYMENT_CHARGE_ROUTING_KEY = "payment.charge";
    public static final String PAYMENT_REFUND_ROUTING_KEY = "payment.refund";

    @Bean
    public FanoutExchange paymentEventsExchange() {
        return new FanoutExchange(PAYMENT_EVENTS_EXCHANGE);
    }

    @Bean
    public DirectExchange paymentDlx() {
        return new DirectExchange(PAYMENT_DLX);
    }

    @Bean
    public Queue paymentDlq() {
        return new Queue(PAYMENT_DLQ);
    }

    @Bean
    public Binding dlqBinding(Queue paymentDlq, DirectExchange paymentDlx) {
        return BindingBuilder.bind(paymentDlq).to(paymentDlx).with(PAYMENT_DLQ);
    }

    @Bean
    public Queue paymentCommandQueue() {
        return new Queue(PAYMENT_COMMAND_QUEUE, true);
    }

    @Bean
    public TopicExchange sagaCommandExchange() {
        return new TopicExchange(SAGA_COMMAND_EXCHANGE);
    }

    @Bean
    public TopicExchange sagaReplyExchange() {
        return new TopicExchange(SAGA_REPLY_EXCHANGE);
    }

    @Bean
    public Binding paymentCommandBinding(Queue paymentCommandQueue, TopicExchange sagaCommandExchange) {
        return BindingBuilder
                .bind(paymentCommandQueue)
                .to(sagaCommandExchange)
                .with("payment.*");
    }

    @Bean
    public Binding paymentChargeBinding(Queue paymentCommandQueue, TopicExchange commandExchange) {
        return BindingBuilder.bind(paymentCommandQueue).to(commandExchange).with(PAYMENT_CHARGE_ROUTING_KEY);
    }
    
    @Bean
    public Binding paymentRefundBinding(Queue paymentCommandQueue, TopicExchange commandExchange) {
        return BindingBuilder.bind(paymentCommandQueue).to(commandExchange).with(PAYMENT_REFUND_ROUTING_KEY);
    }

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
} 