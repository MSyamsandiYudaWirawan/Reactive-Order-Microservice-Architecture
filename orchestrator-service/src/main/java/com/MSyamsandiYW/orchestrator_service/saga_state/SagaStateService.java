package com.MSyamsandiYW.orchestrator_service.saga_state;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface SagaStateService {

    Mono<SagaState> findByTransactionId(String transactionId);

    Mono<SagaState> create(String transactionId, String correlationId);

    Mono<Integer> updateStatusIfInProgress(String transactionId, String newSagaStatus, String newPaymentStatus);

    Mono<SagaState> save(SagaState sagaState);

    Flux<SagaState> findAllExpiredTransaction(Instant cutoff);

    Mono<SagaState> findOrCreate(String transactionId, String correlationId);

    Mono<Void> updateCompensatingStatus(String transactionId, String name);
}
