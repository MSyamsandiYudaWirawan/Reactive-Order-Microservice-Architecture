package com.MSyamsandiYW.payment_service.config;

import com.MSyamsandiYW.common.redis.RedisService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

@Configuration
public class RedisServiceConfig {

    @Bean
    public RedisService redisService(ReactiveRedisTemplate<String, String> redisTemplate) {
        return new RedisService(redisTemplate);
    }
}
