package com.MSyamsandiYW.payment_service.payment;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface PaymentRepository extends R2dbcRepository<Payment, UUID> {

    Mono<Payment> findFirstByTransactionIdOrderByCreatedAtDesc(String transactionId);

    Flux<Payment> findByUserId(String transactionId);

    Mono<Payment> findFirstByTransactionIdAndStatus(String transactionId, String status);

    @Query("""
                SELECT * FROM payments
                WHERE status = 'PENDING'
                  AND created_at < :cutoff
            """)
    Flux<Payment> findAllExpiredPayments(Instant cutoff);

    @Modifying
    @Query("""
                UPDATE payments
                SET status = :status,
                    updated_by = 'PAYMENT_SERVICE',
                    updated_at = NOW()
                WHERE id = :id AND status = 'PENDING'
            """)
    Mono<Integer> updatePendingStatusPayment(UUID id, String status);

    @Modifying
    @Query("""
                UPDATE payments
                SET status = :status,
                    failure_code = :failureCode,
                    failure_message = :failureMessage,
                    updated_by = 'PAYMENT_SERVICE',
                    updated_at = NOW()
                WHERE id = :id
                AND status != :status
            """)
    Mono<Integer> updateStatusPayment(UUID id, String status, String failureCode, String failureMessage);
}
