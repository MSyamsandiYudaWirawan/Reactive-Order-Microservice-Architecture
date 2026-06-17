package com.MSyamsandiYW.payment_service.payment;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PaymentRepository extends R2dbcRepository<Payment, UUID> {

    Mono<Payment> findByTransactionId(String transactionId);

    Flux<Payment> findByUserId(String transactionId);

    Mono<Payment> findFirstByTransactionIdAndStatus(String transactionId, String status);
}
