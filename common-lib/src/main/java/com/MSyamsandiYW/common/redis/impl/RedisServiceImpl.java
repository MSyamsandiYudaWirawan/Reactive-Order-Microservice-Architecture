package com.MSyamsandiYW.common.redis.impl;

import com.MSyamsandiYW.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    @Override
    public Mono<Boolean> isDuplicate(String key) {
        return reactiveRedisTemplate.hasKey(key);
    }

    @Override
    public Mono<Boolean> store(String key, String value) {
        return reactiveRedisTemplate.opsForValue()
                .set(key, value, DEFAULT_TTL);
    }

    @Override
    public Mono<Boolean> storeIfAbsent(String key, String value) {
        return reactiveRedisTemplate.opsForValue()
                .setIfAbsent(key, value, DEFAULT_TTL);
    }
}
