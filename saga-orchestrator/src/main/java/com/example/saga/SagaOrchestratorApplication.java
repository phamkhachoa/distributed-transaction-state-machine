package com.example.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot Application for Saga Orchestrator
 */
@SpringBootApplication
@EnableScheduling
public class SagaOrchestratorApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SagaOrchestratorApplication.class, args);
    }
} 