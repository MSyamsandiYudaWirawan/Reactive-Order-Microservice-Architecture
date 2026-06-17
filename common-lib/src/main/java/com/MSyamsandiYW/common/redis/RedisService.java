package com.MSyamsandiYW.common.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RequiredArgsConstructor
public class RedisService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    public Mono<Boolean> isDuplicate(String key) {
        return reactiveRedisTemplate.hasKey(key);
    }

    public Mono<Boolean> store(String key, String value) {
        return reactiveRedisTemplate.opsForValue()
                .set(key, value, DEFAULT_TTL);
    }

    public Mono<Boolean> storeIfAbsent(String key, String value) {
        return reactiveRedisTemplate.opsForValue()
                .setIfAbsent(key, value, DEFAULT_TTL);
    }
}
