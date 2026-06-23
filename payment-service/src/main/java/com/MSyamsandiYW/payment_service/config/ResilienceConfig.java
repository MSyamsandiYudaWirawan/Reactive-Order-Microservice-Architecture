package com.MSyamsandiYW.payment_service.config;

import com.MSyamsandiYW.common.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    // retry for general retry stateless
    // max 3 retry with exponential backoff
    // if exception is instance of Exception, then retry
    // if exception is instance of BusinessException, then don't retry
    @Bean
    public Retry retry() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(500, 2, 0.2))
                .retryExceptions(Exception.class)
                .ignoreExceptions(BusinessException.class)
                .build();
        return Retry.of("payment-command", retryConfig);
    }

    // circuit breaker for inventory service stateful
    @Bean
    public CircuitBreaker circuitBreaker(){
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // if 50% or more of calls fail, trip the circuit to OPEN
                .waitDurationInOpenState(Duration.ofMillis(30000)) // once OPEN, reject all calls for 30s, then transition to HALF_OPEN to test if downstream recovered
                .slidingWindowSize(10) //check last 10 calls
                .minimumNumberOfCalls(5) // don't evaluate failure rate until at least 5 calls happened
                .build();
        return CircuitBreaker.of("inventory-service",config);
    }
}
