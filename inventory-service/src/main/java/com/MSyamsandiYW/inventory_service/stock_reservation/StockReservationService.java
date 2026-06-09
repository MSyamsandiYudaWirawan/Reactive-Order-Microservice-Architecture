package com.MSyamsandiYW.inventory_service.stock_reservation;

import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
import reactor.core.publisher.Mono;

import java.util.List;

public interface StockReservationService {
    Mono<List<StockReservation>> reserveStock(StockCommand command);

    Mono<List<StockReservation>> updateStatusReservation(String transactionId, String statusReservation);
}
