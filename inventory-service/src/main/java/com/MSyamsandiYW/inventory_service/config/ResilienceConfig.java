package com.MSyamsandiYW.inventory_service.config;

import com.MSyamsandiYW.common.exception.BusinessException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RetryResilience4jConfig {

    @Bean
    public Retry retry() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(500, 2, 0.2)) // adds 20% jitter
                .retryExceptions(Exception.class)
                .ignoreExceptions(BusinessException.class)
                .build();
        return Retry.of("inventory-stock-command", retryConfig);
    }
}
