package com.example.shippingservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String SAGA_COMMAND_EXCHANGE = "saga.command.exchange";
    public static final String SAGA_REPLY_EXCHANGE = "saga.reply.exchange";
    public static final String SAGA_REPLY_QUEUE_ROUTING_KEY = "saga.reply.queue";

    // Shipping-specific queue
    public static final String SHIPPING_COMMAND_QUEUE = "shipping.command.queue";
    // Routing keys
    public static final String SHIPPING_SCHEDULE_ROUTING_KEY = "shipping.schedule";
    public static final String SHIPPING_CANCEL_ROUTING_KEY = "shipping.cancel";

    @Bean
    public Queue shippingCommandQueue() {
        return new Queue(SHIPPING_COMMAND_QUEUE, true);
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
    public Binding shippingCommandBinding(Queue shippingCommandQueue, TopicExchange sagaCommandExchange) {
        return BindingBuilder
                .bind(shippingCommandQueue)
                .to(sagaCommandExchange)
                .with("shipping.*");
    }

    @Bean
    public Binding shippingScheduleBinding(Queue shippingCommandQueue, TopicExchange commandExchange) {
        return BindingBuilder.bind(shippingCommandQueue).to(commandExchange).with(SHIPPING_SCHEDULE_ROUTING_KEY);
    }
    
    @Bean
    public Binding shippingCancelBinding(Queue shippingCommandQueue, TopicExchange commandExchange) {
        return BindingBuilder.bind(shippingCommandQueue).to(commandExchange).with(SHIPPING_CANCEL_ROUTING_KEY);
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