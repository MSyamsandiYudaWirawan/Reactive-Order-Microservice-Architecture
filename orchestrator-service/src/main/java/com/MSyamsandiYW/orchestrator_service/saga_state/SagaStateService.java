package com.MSyamsandiYW.orchestrator_service.saga_state;

import reactor.core.publisher.Mono;

public interface SagaStateService {

    Mono<SagaState> findByTransactionId(String transactionId);

    Mono<SagaState> create(String transactionId, String correlationId);

    Mono<SagaState> save(SagaState sagaState);
}
