package com.example.common.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ComponentScan
@EnableJpaRepositories
@EntityScan
@EnableScheduling
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxAutoConfiguration {
} 