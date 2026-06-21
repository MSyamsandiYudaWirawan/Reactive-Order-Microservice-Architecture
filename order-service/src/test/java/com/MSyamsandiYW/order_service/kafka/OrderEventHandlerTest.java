package com.MSyamsandiYW.order_service.kafka;

import com.MSyamsandiYW.order_service.kafka.event.OrderCommand;
import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.OrderRepository;
import com.MSyamsandiYW.order_service.order_ledger.OrderLedgerService;
import com.MSyamsandiYW.order_service.properties.AppConstant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
    private Order order;

    @BeforeEach
    void setUp() {
        command = OrderCommand.builder()
                .transactionId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .build();

        order = Order.builder()
                .id(UUID.randomUUID())
                .transactionId(command.getTransactionId())
                .orderStatus(AppConstant.ORDER_STATUS.PENDING.name())
                .createdDate(Instant.now())
                .build();
    }

    @Test
    @DisplayName("handleStockReservedCompleted - should update status to WAITING_PAYMENT")
    void handleStockReservedCompleted() {
        when(orderRepository.findByTransactionId(command.getTransactionId())).thenReturn(Mono.just(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockReservedCompleted(command))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getOrderStatus().equals("WAITING_PAYMENT")));
    }

    @Test
    @DisplayName("handlePaymentCompleted - should update status to PAID")
    void handlePaymentCompleted() {
        when(orderRepository.findByTransactionId(command.getTransactionId())).thenReturn(Mono.just(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handlePaymentCompleted(command))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getOrderStatus().equals("PAID")));
    }

    @Test
    @DisplayName("handleOrderCompleted - should update status to COMPLETED")
    void handleOrderCompleted() {
        when(orderRepository.findByTransactionId(command.getTransactionId())).thenReturn(Mono.just(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleOrderCompleted(command))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getOrderStatus().equals("COMPLETED")));
    }

    @Test
    @DisplayName("handleRefundCompleted - should update status to REFUNDED")
    void handleRefundCompleted() {
        when(orderRepository.findByTransactionId(command.getTransactionId())).thenReturn(Mono.just(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleRefundCompleted(command))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getOrderStatus().equals("REFUNDED")));
    }

    @Test
    @DisplayName("handleStockOutOfStock - should update status to OUT_OF_STOCK")
    void handleStockOutOfStock() {
        when(orderRepository.findByTransactionId(command.getTransactionId())).thenReturn(Mono.just(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockOutOfStock(command))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getOrderStatus().equals("OUT_OF_STOCK")));
    }

    @Test
    @DisplayName("handleOrderExpired - should update status to EXPIRED")
    void handleOrderExpired() {
        when(orderRepository.findByTransactionId(command.getTransactionId())).thenReturn(Mono.just(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleOrderExpired(command))
                .verifyComplete();

        verify(orderRepository).save(argThat(o -> o.getOrderStatus().equals("EXPIRED")));
    }

    @Test
    @DisplayName("updateOrderStatus - order not found should complete without saving")
    void updateOrderStatus_orderNotFound() {
        when(orderRepository.findByTransactionId(command.getTransactionId())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleOrderCompleted(command))
                .verifyComplete();

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateOrderStatus - should set failure fields when present")
    void updateOrderStatus_withFailureInfo() {
        command.setFailureCode("OUT_OF_STOCK");
        command.setFailureMessage("Insufficient stock");

        when(orderRepository.findByTransactionId(command.getTransactionId())).thenReturn(Mono.just(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(orderLedgerService.recordOrderEvent(any())).thenReturn(Mono.empty());

        StepVerifier.create(handler.handleStockOutOfStock(command))
                .verifyComplete();

        verify(orderRepository).save(argThat(o ->
                "OUT_OF_STOCK".equals(o.getFailureCode()) &&
                        "Insufficient stock".equals(o.getFailureMessage())
        ));
    }
}
