package com.MSyamsandiYW.inventory_service.stock_ledger;

import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import reactor.core.publisher.Mono;

import java.util.List;

public interface StockLedgerService {
    Mono<Void> recordStockEvent(List<StockReservation> reservationList);
}
