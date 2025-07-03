package com.example.orderservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public TopicExchange sagaRequestTopic() {
        return new TopicExchange("saga-requests");
    }

    @Bean
    public Queue orderStatusUpdateQueue() {
        return new Queue("order-status-update");
    }
} 