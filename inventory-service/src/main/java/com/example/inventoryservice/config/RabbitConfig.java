package com.example.inventoryservice.config;

import com.example.inventoryservice.model.OrderItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RabbitConfig {

    public static final String SAGA_COMMAND_EXCHANGE = "saga.command.exchange";
    public static final String SAGA_REPLY_EXCHANGE = "saga.reply.exchange";
    public static final String INVENTORY_COMMAND_QUEUE = "inventory.command.queue";
    
    @Bean
    public Queue inventoryCommandQueue() {
        return new Queue(INVENTORY_COMMAND_QUEUE, true);
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
    public Binding inventoryCommandBinding(Queue inventoryCommandQueue, TopicExchange sagaCommandExchange) {
        return BindingBuilder
                .bind(inventoryCommandQueue)
                .to(sagaCommandExchange)
                .with("inventory.*");
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

    @Bean
    public Queue inventoryRequestsQueue() {
        return new Queue("inventory-requests", false);
    }
    
    @Bean
    public ConcurrentHashMap<String, List<OrderItem>> reservations() {
        return new ConcurrentHashMap<>();
    }
} 