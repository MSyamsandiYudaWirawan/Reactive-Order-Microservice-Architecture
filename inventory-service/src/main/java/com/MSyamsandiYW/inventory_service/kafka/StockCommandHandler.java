package com.MSyamsandiYW.inventory_service.kafka;

import com.MSyamsandiYW.inventory_service.kafka.event.OrderStatusEvent;
import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
import com.MSyamsandiYW.inventory_service.product.ProductService;
import com.MSyamsandiYW.inventory_service.stock_ledger.StockLedgerService;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;

import java.util.UUID;

import static com.MSyamsandiYW.inventory_service.properties.AppConstant.RESERVATION_STATUS.DEDUCTED;
import static com.MSyamsandiYW.inventory_service.properties.AppConstant.RESERVATION_STATUS.RELEASED;
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
                    OrderStatusEvent event = OrderStatusEvent.builder()
                            .correlationId(record.value().getCorrelationId())
                            .transactionId(record.value().getTransactionId())
                            .build();
                    return stockEventProducer.send(STOCK_RESERVE_COMPLETED, UUID.randomUUID().toString(), event);
                }));
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
}
