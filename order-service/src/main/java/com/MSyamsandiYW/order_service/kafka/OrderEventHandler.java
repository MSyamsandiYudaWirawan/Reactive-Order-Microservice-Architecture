package com.MSyamsandiYW.order_service.kafka;

import com.MSyamsandiYW.order_service.kafka.event.OrderCommand;
import com.MSyamsandiYW.order_service.order.Order;
import com.MSyamsandiYW.order_service.order.OrderRepository;
import com.MSyamsandiYW.order_service.order_ledger.OrderStatusHistoryService;
import com.MSyamsandiYW.order_service.properties.AppConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

import static com.MSyamsandiYW.order_service.properties.AppConstant.ORDER_STATUS.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventHandler {
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryService orderStatusHistoryService;

    // Defines which statuses are allowed to transition TO a given target status
    private static final Map<AppConstant.ORDER_STATUS, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            WAITING_PAYMENT, Set.of(PENDING.name()),
            PAID, Set.of(PENDING.name(), WAITING_PAYMENT.name()),
            COMPLETED, Set.of(PENDING.name(), WAITING_PAYMENT.name(), PAID.name()),
            OUT_OF_STOCK, Set.of(PENDING.name(), WAITING_PAYMENT.name()),
            REFUNDED, Set.of(PAID.name(), OUT_OF_STOCK.name(), REFUND_FAILED.name()),
            REFUND_FAILED, Set.of(PAID.name(), OUT_OF_STOCK.name()),
            EXPIRED, Set.of(PENDING.name(), WAITING_PAYMENT.name())
    );

    public Mono<Void> handleStockReservedCompleted(OrderCommand payload) {
        return updateOrderStatus(payload, WAITING_PAYMENT);
    }

    public Mono<Void> handlePaymentCompleted(OrderCommand payload) {
        return updateOrderStatus(payload, PAID);
    }

    public Mono<Void> handleOrderCompleted(OrderCommand payload) {
        return updateOrderStatus(payload, COMPLETED);
    }

    public Mono<Void> handleRefundCompleted(OrderCommand payload) {
        return updateOrderStatus(payload, REFUNDED);
    }

    public Mono<Void> handleStockOutOfStock(OrderCommand payload) {
        return updateOrderStatus(payload, OUT_OF_STOCK);
    }

    public Mono<Void> handleRefundFailed(OrderCommand payload) {
        return updateOrderStatus(payload, REFUND_FAILED);
    }

    public Mono<Void> handleOrderExpired(OrderCommand payload) {
        return updateOrderStatus(payload, EXPIRED);
    }


    public Mono<Void> updateOrderStatus(OrderCommand payload, AppConstant.ORDER_STATUS targetStatus) {
        return orderRepository.updateOrderStatus(
                        payload.getTransactionId(),
                        targetStatus.name(),
                        payload.getFailureCode(),
                        payload.getFailureMessage(),
                        ALLOWED_TRANSITIONS.get(targetStatus))
                .filter(updatedRows -> updatedRows > 0)
                .flatMap(__ -> {
                    log.info("Order status updated to {} - transactionId: {}, correlationId: {}",
                            targetStatus.name(),payload.getTransactionId(),payload.getCorrelationId());
                    Order order = Order.builder()
                            .transactionId(payload.getTransactionId())
                            .correlationId(payload.getCorrelationId())
                            .orderStatus(targetStatus.name())
                            .build();
                    return orderStatusHistoryService.recordOrderEvent(order);
                })
                .then();
    }
}
