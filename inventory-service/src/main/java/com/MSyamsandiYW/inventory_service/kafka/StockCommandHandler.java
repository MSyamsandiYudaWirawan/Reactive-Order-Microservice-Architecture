package com.MSyamsandiYW.inventory_service.kafka;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.inventory_service.kafka.event.StockEventPayload;
import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
import com.MSyamsandiYW.inventory_service.product.ProductService;
import com.MSyamsandiYW.inventory_service.properties.AppConstant;
import com.MSyamsandiYW.inventory_service.stock_ledger.StockLedgerService;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;

import java.util.UUID;

import static com.MSyamsandiYW.inventory_service.properties.AppConstant.RESERVATION_STATUS.*;
import static com.MSyamsandiYW.inventory_service.properties.AppConstant.TOPICS.STOCK_RESERVE_COMPLETED;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockCommandHandler {

    private final ProductService productService;
    private final StockReservationService stockReservationService;
    private final StockLedgerService stockLedgerService;
    private final StockEventProducer stockEventProducer;

    public Mono<Void> handleStockReserve(ReceiverRecord<String, StockCommand> record) {
        //create stock reservation
        return stockReservationService.reserveStock(record.value())
                //update product available qty and reserved qty
                .flatMap(reservationList -> productService.reserveStock(reservationList).thenReturn(reservationList))
                //record the event to stock ledger
                .flatMap(reservationList -> stockLedgerService.recordStockEvent(reservationList).then())
                //produce event reserve completed
                .then(Mono.defer(() -> {
                    StockEventPayload event = StockEventPayload.builder()
                            .correlationId(record.value().getCorrelationId())
                            .transactionId(record.value().getTransactionId())
                            .build();
                    return stockEventProducer.send(STOCK_RESERVE_COMPLETED, UUID.randomUUID().toString(), event);
                }))
                //handle out of stock
                .onErrorResume(BusinessException.class, e -> {
                    if (e.getErrorCode().equals(ErrorCode.OUT_OF_STOCK)) {
                        return handleOutOfStock(record.value());
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> handleReleaseStock(ReceiverRecord<String, StockCommand> record) {
        // find reservation by transaction id and set status = RELEASED
        return stockReservationService.updateStatusReservation(record.value().getTransactionId(), RELEASED.name())
                //update product available qty and reserved qty
                .flatMap(reservationList -> productService.releaseStock(reservationList).thenReturn(reservationList))
                //record the event to stock ledger
                .flatMap(reservationList -> stockLedgerService.recordStockEvent(reservationList).then());
    }

    public Mono<Void> handleDeductStock(ReceiverRecord<String, StockCommand> record) {
        // find reservation by transaction id and set status = DEDUCTED
        return stockReservationService.updateStatusReservation(record.value().getTransactionId(), DEDUCTED.name())
                //update product available qty and reserved qty
                .flatMap(reservationList -> productService.deductStock(reservationList).thenReturn(reservationList))
                //record the event to stock ledger
                .flatMap(reservationList -> stockLedgerService.recordStockEvent(reservationList).then());
    }

    private Mono<Void> handleOutOfStock(StockCommand payload) {
        // find reservation by transaction id and set status = OUT_OF_STOCK
        return stockReservationService.updateStatusReservation(payload.getTransactionId(), OUT_OF_STOCK.name())
                //record the event to stock ledger
                .flatMap(stockLedgerService::recordStockEvent)
                //produce event reserve failed out of stock will be consumed by fulfillment-service
                .then(Mono.defer( () -> {
                    StockEventPayload eventPayload = StockEventPayload.builder()
                            .correlationId(payload.getCorrelationId())
                            .transactionId(payload.getTransactionId())
                            .failureCode(ErrorCode.OUT_OF_STOCK.name())
                            .failureMessage(ErrorCode.OUT_OF_STOCK.getDefaultMessage())
                            .build();

                    return stockEventProducer.send(AppConstant.TOPICS.OUT_OF_STOCK,UUID.randomUUID().toString(),eventPayload);
                }));
    }
}
