package com.MSyamsandiYW.common.redis;

import reactor.core.publisher.Mono;

public interface RedisService {
    Mono<Boolean> isDuplicate(String key);

    Mono<Boolean> store(String key, String value);

    Mono<Boolean> storeIfAbsent(String key, String value);
}
