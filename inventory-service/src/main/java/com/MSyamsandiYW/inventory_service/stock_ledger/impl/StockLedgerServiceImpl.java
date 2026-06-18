package com.MSyamsandiYW.inventory_service.stock_ledger.impl;

import com.MSyamsandiYW.inventory_service.stock_ledger.StockLedger;
import com.MSyamsandiYW.inventory_service.stock_ledger.StockLedgerRepository;
import com.MSyamsandiYW.inventory_service.stock_ledger.StockLedgerService;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockLedgerServiceImpl implements StockLedgerService {
    private final StockLedgerRepository repository;

    @Override
    public Mono<Void> recordStockEvent(List<StockReservation> reservationList) {
        List<StockLedger> ledgerList = reservationList.stream().map(reservation ->
                        StockLedger.builder()
                                .productId(reservation.getProductId())
                                .transactionId(reservation.getTransactionId())
                                .correlationId(reservation.getCorrelationId())
                                .eventType(reservation.getStatus())
                                .qty(reservation.getQty())
                                .createdDate(Instant.now())
                                .build())
                .toList();
        return repository.saveAll(ledgerList).then();
    }
}
