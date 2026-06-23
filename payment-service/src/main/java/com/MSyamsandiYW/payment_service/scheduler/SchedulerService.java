package com.MSyamsandiYW.payment_service.scheduler;

import reactor.core.publisher.Mono;

public interface SchedulerService {
    Mono<Void> executeScheduler();
}
