package com.MSyamsandiYW.inventory_service.kafka;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
import com.MSyamsandiYW.inventory_service.kafka.event.StockItem;
import com.MSyamsandiYW.inventory_service.product.Product;
import com.MSyamsandiYW.inventory_service.product.ProductService;
import com.MSyamsandiYW.inventory_service.properties.AppConstant;
import com.MSyamsandiYW.inventory_service.stock_ledger.StockLedgerService;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockCommandHandlerTest {

    @Mock
    private ProductService productService;
    @Mock
    private StockReservationService stockReservationService;
    @Mock
    private StockLedgerService stockLedgerService;
    @Mock
    private StockEventProducer stockEventProducer;

    @InjectMocks
    private StockCommandHandler handler;

    @Mock
    private ReceiverRecord<String, StockCommand> record;

    private StockCommand command;
    private List<StockReservation> reservations;

    @BeforeEach
    void setUp() {
        command = StockCommand.builder()
                .orderId(UUID.randomUUID().toString())
                .transactionId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .items(List.of(StockItem.builder().productId("prod-1").quantity(5).build()))
                .build();

        reservations = List.of(
                StockReservation.builder()
                        .id(UUID.randomUUID())
                        .productId("prod-1")
                        .transactionId(command.getTransactionId())
                        .qty(5)
                        .status(AppConstant.RESERVATION_STATUS.RESERVED.name())
                        .build()
        );
    }

    @Test
    @DisplayName("handleStockReserve - happy path should reserve stock and produce event")
    void handleStockReserve_happyPath() {
        when(record.value()).thenReturn(command);
        when(stockReservationService.reserveStock(command)).thenReturn(Mono.just(reservations));
        when(productService.reserveStock(reservations)).thenReturn(Mono.just(List.of()));
        when(stockLedgerService.recordStockEvent(reservations)).thenReturn(Mono.empty());
        when(stockEventProducer.send(eq(AppConstant.TOPICS.STOCK_RESERVE_COMPLETED), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockReserve(record))
                .verifyComplete();

        verify(stockReservationService).reserveStock(command);
        verify(productService).reserveStock(reservations);
        verify(stockEventProducer).send(eq(AppConstant.TOPICS.STOCK_RESERVE_COMPLETED), any(), any());
    }

    @Test
    @DisplayName("handleStockReserve - out of stock should produce OUT_OF_STOCK event")
    void handleStockReserve_outOfStock() {
        when(record.value()).thenReturn(command);
        when(stockReservationService.reserveStock(command))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.OUT_OF_STOCK)));
        when(stockReservationService.updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.OUT_OF_STOCK.name()))
                .thenReturn(Mono.just(reservations));
        when(stockLedgerService.recordStockEvent(any(List.class))).thenReturn(Mono.empty());
        when(stockEventProducer.send(eq(AppConstant.TOPICS.OUT_OF_STOCK), any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockReserve(record))
                .verifyComplete();

        verify(stockEventProducer).send(eq(AppConstant.TOPICS.OUT_OF_STOCK), any(), any());
    }

    @Test
    @DisplayName("handleReleaseStock - should release stock and update reservation")
    void handleReleaseStock_happyPath() {
        when(record.value()).thenReturn(command);
        when(stockReservationService.updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.RELEASED.name()))
                .thenReturn(Mono.just(reservations));
        when(productService.releaseStock(reservations)).thenReturn(Mono.just(List.of()));
        when(stockLedgerService.recordStockEvent(reservations)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleReleaseStock(record))
                .verifyComplete();

        verify(stockReservationService).updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.RELEASED.name());
        verify(productService).releaseStock(reservations);
    }

    @Test
    @DisplayName("handleDeductStock - should deduct stock and update reservation")
    void handleDeductStock_happyPath() {
        when(record.value()).thenReturn(command);
        when(stockReservationService.updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.DEDUCTED.name()))
                .thenReturn(Mono.just(reservations));
        when(productService.deductStock(reservations)).thenReturn(Mono.just(List.of()));
        when(stockLedgerService.recordStockEvent(reservations)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleDeductStock(record))
                .verifyComplete();

        verify(stockReservationService).updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.DEDUCTED.name());
        verify(productService).deductStock(reservations);
    }
}
