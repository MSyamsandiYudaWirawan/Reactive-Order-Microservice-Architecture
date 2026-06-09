package com.MSyamsandiYW.inventory_service.stock_reservation;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface StockReservationRepository extends R2dbcRepository<StockReservation, UUID> {
    Flux<StockReservation> findAllByTransactionId(String transactionId);
}
