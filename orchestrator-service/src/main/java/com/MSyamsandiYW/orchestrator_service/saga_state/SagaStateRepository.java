package com.MSyamsandiYW.orchestrator_service.saga_state;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SagaStateRepository extends R2dbcRepository<SagaState, UUID> {

    Mono<SagaState> findFirstByTransactionId(String transactionId);
}
