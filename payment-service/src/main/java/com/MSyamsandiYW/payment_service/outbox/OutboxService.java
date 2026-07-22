package com.MSyamsandiYW.payment_service.outbox;

import reactor.core.publisher.Mono;

public interface OutboxService {
    Mono<Void> save(Outbox outbox);
}
