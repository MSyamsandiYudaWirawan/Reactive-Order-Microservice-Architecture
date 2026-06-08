package com.MSyamsandiYW.order_service.config;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.common.redis.impl.RedisServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

@Configuration
public class RedisServiceConfig {

    @Bean
    public RedisService redisService(ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        return new RedisServiceImpl(reactiveRedisTemplate);
    }
}
