package com.MSyamsandiYW.orchestrator_service.saga_state;

import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface SagaStateService {

    Mono<SagaState> findByTransactionId(String transactionId);

    Mono<SagaState> create(String transactionId, String correlationId);

    Mono<Integer> updateStatusIfInProgress(String transactionId, String newSagaStatus, String newPaymentStatus);

    Mono<Integer> updateStatusIfInProgress(String transactionId, String newSagaStatus, String newPaymentStatus, String failureCode, String failureMessage);

    Mono<SagaState> save(SagaState sagaState);

    Flux<SagaState> findAllExpiredTransaction(Instant cutoff);

    Mono<SagaState> findOrCreate(OrchestratorCommand command);

    Mono<Void> updateCompensatingStatus(String transactionId, String name);
}
