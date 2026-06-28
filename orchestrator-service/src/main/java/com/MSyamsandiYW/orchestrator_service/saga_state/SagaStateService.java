package com.MSyamsandiYW.orchestrator_service.saga_state;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface SagaStateService {

    Mono<SagaState> findByTransactionId(String transactionId);

    Mono<SagaState> create(String transactionId, String correlationId);

    Mono<Integer> updateStatusIfInProgress(String transactionId, String newSagaStatus, String newPaymentStatus);

    Mono<SagaState> save(SagaState sagaState);
    Flux<SagaState> saveAll(List<SagaState> sagaStateList);

    Flux<SagaState> findAllExpiredTransaction(Instant cutoff);

    Mono<SagaState> findOrCreate(String transactionId, String correlationId);
}
