package com.MSyamsandiYW.inventory_service.stock_reservation;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StockReservationRepository extends R2dbcRepository<StockReservation, UUID> {
    Flux<StockReservation> findAllByTransactionId(String transactionId);

    @Query("""
                UPDATE stock_reservation SET status = :status,updated_by = 'INVENTORY_SERVICE', updated_at = now()
                WHERE transaction_id = :transactionId and status = 'RESERVED'
            """
    )
    @Modifying
    Mono<Integer> updateStatus(String transactionId, String status);
}
