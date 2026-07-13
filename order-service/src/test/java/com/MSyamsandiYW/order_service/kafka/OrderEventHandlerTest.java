package com.MSyamsandiYW.order_service.kafka;

import com.MSyamsandiYW.order_service.kafka.event.OrderCommand;
import com.MSyamsandiYW.order_service.order.OrderRepository;
import com.MSyamsandiYW.order_service.order_ledger.OrderLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventHandlerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderLedgerService orderLedgerService;

    @InjectMocks
    private OrderEventHandler handler;

    private OrderCommand command;

    @BeforeEach
    void setUp() {
        command = OrderCommand.builder()
                .transactionId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    @Test
    @DisplayName("handleStockReservedCompleted - should update status to WAITING_PAYMENT")
    void handleStockReservedCompleted() {
        when(orderRepository.updateOrderStatus(eq(command.getTransactionId()), eq("WAITING_PAYMENT"), any(), any(), any(Set.class)))
                .thenReturn(Mono.just(1));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockReservedCompleted(command))
                .verifyComplete();

        verify(orderRepository).updateOrderStatus(eq(command.getTransactionId()), eq("WAITING_PAYMENT"), any(), any(), any(Set.class));
    }

    @Test
    @DisplayName("handlePaymentCompleted - should update status to PAID")
    void handlePaymentCompleted() {
        when(orderRepository.updateOrderStatus(eq(command.getTransactionId()), eq("PAID"), any(), any(), any(Set.class)))
                .thenReturn(Mono.just(1));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handlePaymentCompleted(command))
                .verifyComplete();

        verify(orderRepository).updateOrderStatus(eq(command.getTransactionId()), eq("PAID"), any(), any(), any(Set.class));
    }

    @Test
    @DisplayName("handleOrderCompleted - should update status to COMPLETED")
    void handleOrderCompleted() {
        when(orderRepository.updateOrderStatus(eq(command.getTransactionId()), eq("COMPLETED"), any(), any(), any(Set.class)))
                .thenReturn(Mono.just(1));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleOrderCompleted(command))
                .verifyComplete();

        verify(orderRepository).updateOrderStatus(eq(command.getTransactionId()), eq("COMPLETED"), any(), any(), any(Set.class));
    }

    @Test
    @DisplayName("handleRefundCompleted - should update status to REFUNDED")
    void handleRefundCompleted() {
        when(orderRepository.updateOrderStatus(eq(command.getTransactionId()), eq("REFUNDED"), any(), any(), any(Set.class)))
                .thenReturn(Mono.just(1));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleRefundCompleted(command))
                .verifyComplete();

        verify(orderRepository).updateOrderStatus(eq(command.getTransactionId()), eq("REFUNDED"), any(), any(), any(Set.class));
    }

    @Test
    @DisplayName("handleStockOutOfStock - should update status to OUT_OF_STOCK")
    void handleStockOutOfStock() {
        when(orderRepository.updateOrderStatus(eq(command.getTransactionId()), eq("OUT_OF_STOCK"), any(), any(), any(Set.class)))
                .thenReturn(Mono.just(1));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockOutOfStock(command))
                .verifyComplete();

        verify(orderRepository).updateOrderStatus(eq(command.getTransactionId()), eq("OUT_OF_STOCK"), any(), any(), any(Set.class));
    }

    @Test
    @DisplayName("handleOrderExpired - should update status to EXPIRED")
    void handleOrderExpired() {
        when(orderRepository.updateOrderStatus(eq(command.getTransactionId()), eq("EXPIRED"), any(), any(), any(Set.class)))
                .thenReturn(Mono.just(1));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleOrderExpired(command))
                .verifyComplete();

        verify(orderRepository).updateOrderStatus(eq(command.getTransactionId()), eq("EXPIRED"), any(), any(), any(Set.class));
    }

    @Test
    @DisplayName("updateOrderStatus - order not found should complete without recording ledger")
    void updateOrderStatus_orderNotFound() {
        when(orderRepository.updateOrderStatus(eq(command.getTransactionId()), eq("COMPLETED"), any(), any(), any(Set.class)))
                .thenReturn(Mono.just(0));

        StepVerifier.create(handler.handleOrderCompleted(command))
                .verifyComplete();

        verify(orderLedgerService, never()).recordOrderEvent(any());
    }

    @Test
    @DisplayName("updateOrderStatus - should pass failure fields when present")
    void updateOrderStatus_withFailureInfo() {
        command.setFailureCode("OUT_OF_STOCK");
        command.setFailureMessage("Insufficient stock");

        when(orderRepository.updateOrderStatus(
                eq(command.getTransactionId()), eq("OUT_OF_STOCK"),
                eq("OUT_OF_STOCK"), eq("Insufficient stock"), any(Set.class)))
                .thenReturn(Mono.just(1));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockOutOfStock(command))
                .verifyComplete();

        verify(orderRepository).updateOrderStatus(
                eq(command.getTransactionId()), eq("OUT_OF_STOCK"),
                eq("OUT_OF_STOCK"), eq("Insufficient stock"), any(Set.class));
    }
}
