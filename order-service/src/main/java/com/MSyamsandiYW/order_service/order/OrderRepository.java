package com.MSyamsandiYW.order_service.order;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends R2dbcRepository<Order, UUID> {

    Mono<Order> findByTransactionId(String transactionId);

    Flux<Order> findAllByUserId(String userId);
}
