package com.MSyamsandiYW.inventory_service.kafka;

import com.MSyamsandiYW.common.exception.BusinessException;
import com.MSyamsandiYW.common.exception.ErrorCode;
import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
import com.MSyamsandiYW.inventory_service.kafka.event.StockItem;
import com.MSyamsandiYW.inventory_service.outbox.OutboxService;
import com.MSyamsandiYW.inventory_service.product.ProductService;
import com.MSyamsandiYW.inventory_service.properties.AppConstant;
import com.MSyamsandiYW.inventory_service.stock_ledger.StockLedgerService;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservation;
import com.MSyamsandiYW.inventory_service.stock_reservation.StockReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCommandHandlerTest {

    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private ProductService productService;
    @Mock
    private StockReservationService stockReservationService;
    @Mock
    private StockLedgerService stockLedgerService;
    @Mock
    private OutboxService outboxService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

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

        // pass the chain through without opening a real transaction
        lenient().when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("handleStockReserve - happy path should reserve stock and write outbox event")
    void handleStockReserve_happyPath() {
        when(record.value()).thenReturn(command);
        when(stockReservationService.reserveStock(command)).thenReturn(Mono.just(reservations));
        when(productService.reserveStock(reservations)).thenReturn(Mono.empty());
        when(stockLedgerService.recordStockEvent(reservations)).thenReturn(Mono.empty());
        when(outboxService.save(any())).thenReturn(Mono.empty());

        // virtual time skips the 15s testing delay in the handler
        StepVerifier.withVirtualTime(() -> handler.handleStockReserve(record))
                .thenAwait(Duration.ofSeconds(16))
                .verifyComplete();

        verify(stockReservationService).reserveStock(command);
        verify(productService).reserveStock(reservations);
        verify(stockLedgerService).recordStockEvent(reservations);
        verify(outboxService).save(argThat(outbox ->
                outbox.getAggregateType().equals(AppConstant.TOPICS.STOCK_RESERVE_COMPLETED)
                        && outbox.getAggregateId().equals(command.getTransactionId())
                        && outbox.getEventType().equals("STOCK_RESERVE_COMPLETED")));
    }

    @Test
    @DisplayName("handleStockReserve - out of stock should write OUT_OF_STOCK outbox event with failure detail")
    void handleStockReserve_outOfStock() {
        when(record.value()).thenReturn(command);
        when(stockReservationService.reserveStock(command)).thenReturn(Mono.just(reservations));
        when(productService.reserveStock(reservations))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.OUT_OF_STOCK)));
        when(stockReservationService.updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.OUT_OF_STOCK.name()))
                .thenReturn(Mono.just(reservations));
        when(stockLedgerService.recordStockEvent(anyList())).thenReturn(Mono.empty());
        when(outboxService.save(any())).thenReturn(Mono.empty());

        StepVerifier.withVirtualTime(() -> handler.handleStockReserve(record))
                .thenAwait(Duration.ofSeconds(16))
                .verifyComplete();

        verify(stockReservationService).updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.OUT_OF_STOCK.name());
        verify(outboxService).save(argThat(outbox ->
                outbox.getAggregateType().equals(AppConstant.TOPICS.OUT_OF_STOCK)
                        && outbox.getAggregateId().equals(command.getTransactionId())
                        && outbox.getPayload().contains(ErrorCode.OUT_OF_STOCK.name())));
    }

    @Test
    @DisplayName("handleReleaseStock - should release stock and update reservation")
    void handleReleaseStock_happyPath() {
        when(record.value()).thenReturn(command);
        when(stockReservationService.updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.RELEASED.name()))
                .thenReturn(Mono.just(reservations));
        when(productService.releaseStock(reservations)).thenReturn(Mono.empty())
        ;
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
        when(productService.deductStock(reservations)).thenReturn(Mono.empty());
        when(stockLedgerService.recordStockEvent(reservations)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleDeductStock(record))
                .verifyComplete();

        verify(stockReservationService).updateStatusReservation(command.getTransactionId(), AppConstant.RESERVATION_STATUS.DEDUCTED.name());
        verify(productService).deductStock(reservations);
    }
}
