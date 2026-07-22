package com.MSyamsandiYW.inventory_service.outbox;

import com.MSyamsandiYW.inventory_service.outbox.Outbox;
import reactor.core.publisher.Mono;

public interface OutboxService {
    Mono<Void> save(Outbox outbox);
}
