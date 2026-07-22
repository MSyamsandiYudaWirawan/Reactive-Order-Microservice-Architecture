package com.MSyamsandiYW.inventory_service.kafka;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
import com.MSyamsandiYW.inventory_service.kafka.event.StockEventPayload;
import com.MSyamsandiYW.inventory_service.outbox.Outbox;
import com.MSyamsandiYW.inventory_service.outbox.OutboxService;
import com.MSyamsandiYW.inventory_service.product.ProductService;
import com.MSyamsandiYW.inventory_service.properties.AppConstant;
import com.MSyamsandiYW.inventory_service.stock_ledger.StockLedgerService;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;

import java.time.Duration;

import static com.MSyamsandiYW.inventory_service.properties.AppConstant.RESERVATION_STATUS.*;
import static com.MSyamsandiYW.inventory_service.properties.AppConstant.TOPICS.STOCK_RESERVE_COMPLETED;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockCommandHandler {

    private final TransactionalOperator transactionalOperator;
    private final ProductService productService;
    private final StockReservationService stockReservationService;
    private final StockLedgerService stockLedgerService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public Mono<Void> handleStockReserve(ReceiverRecord<String, StockCommand> record) {
        log.info("Reserving stock - transactionId: {}, correlationId: {}", record.value().getTransactionId(), record.value().getCorrelationId());
        //create stock reservations
        // TODO: Remove delay — testing only (simulates slow stock check so payment completes first)
        return Mono.delay(Duration.ofSeconds(15))
                //create stock reservations — committed on its own so the OUT_OF_STOCK path can still find and update them
                .then(stockReservationService.reserveStock(record.value()))
                // update product qty, record stock ledger and insert outbox in one transaction
                .flatMap(reservationList -> productService.reserveStock(reservationList).thenReturn(reservationList)
                        .then(stockLedgerService.recordStockEvent(reservationList))
                        .then(insertOutbox(buildEventPayload(record.value()), STOCK_RESERVE_COMPLETED, "STOCK_RESERVE_COMPLETED"))
                        // flag as transactional
                        .as(transactionalOperator::transactional)
                )

                //handle out of stock
                .onErrorResume(BusinessException.class, e -> {
                    if (e.getErrorCode().equals(ErrorCode.OUT_OF_STOCK)) {
                        log.warn("Out of stock - transactionId: {}, correlationId: {}", record.value().getTransactionId(), record.value().getCorrelationId());
                        return handleOutOfStock(record.value());
                    }
                    return Mono.empty();
                });
    }


    public Mono<Void> handleReleaseStock(ReceiverRecord<String, StockCommand> record) {
        log.info("Releasing stock - transactionId: {}, correlationId: {}", record.value().getTransactionId(), record.value().getCorrelationId());
        return stockReservationService.updateStatusReservation(record.value().getTransactionId(), RELEASED.name())
                //update product available qty and reserved qty
                .flatMap(reservationList -> productService.releaseStock(reservationList).thenReturn(reservationList))
                //record the event to stock ledger
                .flatMap(reservationList -> stockLedgerService.recordStockEvent(reservationList).then());
    }

    public Mono<Void> handleDeductStock(ReceiverRecord<String, StockCommand> record) {
        log.info("Deducting stock - transactionId: {}, correlationId: {}", record.value().getTransactionId(), record.value().getCorrelationId());
        return stockReservationService.updateStatusReservation(record.value().getTransactionId(), DEDUCTED.name())
                //update product available qty and reserved qty
                .flatMap(reservationList -> productService.deductStock(reservationList).thenReturn(reservationList))
                //record the event to stock ledger
                .flatMap(reservationList -> stockLedgerService.recordStockEvent(reservationList).then());
    }

    private Mono<Void> handleOutOfStock(StockCommand payload) {

        StockEventPayload payloadEvent = StockEventPayload.builder()
                .transactionId(payload.getTransactionId())
                .correlationId(payload.getCorrelationId())
                .failureCode(ErrorCode.OUT_OF_STOCK.name())
                .failureMessage(ErrorCode.OUT_OF_STOCK.getDefaultMessage())
                .build();

        // find reservation by transaction id and set status = OUT_OF_STOCK
        return stockReservationService.updateStatusReservation(payload.getTransactionId(), OUT_OF_STOCK.name())
                // record the event to stock ledger
                .flatMap(stockLedgerService::recordStockEvent)
                // insert outbox with failure detail
                .then(insertOutbox(payloadEvent, AppConstant.TOPICS.OUT_OF_STOCK, "OUT_OF_STOCK"))
                // flag as transactional
                .as(transactionalOperator::transactional)
                .then();
    }

    private StockEventPayload buildEventPayload(StockCommand command) {
        return StockEventPayload.builder()
                .transactionId(command.getTransactionId())
                .correlationId(command.getCorrelationId())
                .build();
    }

    private Mono<Void> insertOutbox(StockEventPayload payload, String topic, String eventName) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload))
                .map(json -> Outbox.builder()
                        .aggregateId(payload.getTransactionId())
                        .aggregateType(topic)
                        .eventType(eventName)
                        .payload(json)
                        .build())
                .flatMap(outboxService::save);
    }
}
