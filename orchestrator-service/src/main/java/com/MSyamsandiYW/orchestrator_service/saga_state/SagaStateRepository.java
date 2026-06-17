package com.MSyamsandiYW.orchestrator_service.saga_state;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
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
    Flux<SagaState> findAllExpiredTransaction(ZonedDateTime cutoff);
}
