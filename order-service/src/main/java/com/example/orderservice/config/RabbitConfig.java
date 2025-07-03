package com.example.orderservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String SAGA_COMMAND_EXCHANGE = "saga.command.exchange";
    public static final String SAGA_REPLY_QUEUE = "saga.reply.queue";
    public static final String SAGA_REPLY_EXCHANGE = "saga.reply.exchange";

    @Bean
    public TopicExchange commandExchange() {
        return new TopicExchange(SAGA_COMMAND_EXCHANGE);
    }

    @Bean
    public DirectExchange replyExchange() {
        return new DirectExchange(SAGA_REPLY_EXCHANGE);
    }

    @Bean
    public Queue replyQueue() {
        return new Queue(SAGA_REPLY_QUEUE);
    }

    @Bean
    public Binding replyBinding(Queue replyQueue, DirectExchange replyExchange) {
        return BindingBuilder.bind(replyQueue).to(replyExchange).with(SAGA_REPLY_QUEUE);
    }
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
} 