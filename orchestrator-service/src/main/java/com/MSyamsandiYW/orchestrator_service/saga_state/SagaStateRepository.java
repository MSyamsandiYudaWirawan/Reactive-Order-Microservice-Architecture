package com.MSyamsandiYW.orchestrator_service.saga_state;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface SagaStateRepository extends R2dbcRepository<SagaState, UUID> {

    @Modifying
    @Query("""
            UPDATE saga_state 
            SET saga_status = :newSagaStatus, 
                payment_status = :newPaymentStatus, 
                updated_by = 'ORCHESTRATION_SERVICE', 
                last_modified_date = NOW()
            WHERE transaction_id = :transactionId 
              AND saga_status = 'IN_PROGRESS'
            """)
    Mono<Integer> updateStatusIfInProgress(String transactionId, String newSagaStatus, String newPaymentStatus);

    Mono<SagaState> findFirstByTransactionId(String transactionId);

    @Query("""
            SELECT * FROM saga_state
            WHERE saga_status = 'IN_PROGRESS'
              AND created_date < :cutoff
            """)
    Flux<SagaState> findAllExpiredTransaction(Instant cutoff);

    @Modifying
    @Query("""
            INSERT INTO saga_state (id, transaction_id, correlation_id, saga_status, created_by, created_date) 
            VALUES (gen_random_uuid(), :transactionId, :correlationId, 'IN_PROGRESS', 'ORCHESTRATION_SERVICE', NOW())
            ON CONFLICT (transaction_id) DO NOTHING 
            """)
    Mono<Integer> insertIfAbsent(String transactionId, String correlationId);
}
