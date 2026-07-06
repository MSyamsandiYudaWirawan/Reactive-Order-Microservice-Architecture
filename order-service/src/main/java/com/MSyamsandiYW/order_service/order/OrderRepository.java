package com.MSyamsandiYW.order_service.order;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends R2dbcRepository<Order, UUID> {

    Mono<Order> findByTransactionId(String transactionId);

    Flux<Order> findAllByUserId(String userId);

    @Query("""
            UPDATE orders SET order_status = :orderStatus, updated_by = 'ORDER_SERVICE', last_modified_date = now()
            WHERE transaction_id = :transactionId
            """
    )
    @Modifying
    Mono<Integer> updateOrderStatus(String transactionId, String orderStatus);
}
