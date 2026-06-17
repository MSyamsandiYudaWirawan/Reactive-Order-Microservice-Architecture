package com.MSyamsandiYW.order_service.kafka;

import com.MSyamsandiYW.order_service.kafka.request.OrderEventRequest;
import com.MSyamsandiYW.order_service.order.OrderRepository;
import com.MSyamsandiYW.order_service.order_ledger.OrderLedgerService;
import com.MSyamsandiYW.order_service.properties.AppConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.MSyamsandiYW.order_service.properties.AppConstant.ORDER_STATUS.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventHandler {
    private final OrderRepository orderRepository;
    private final OrderLedgerService orderLedgerService;

    public Mono<Void> handleStockReservedCompleted(OrderEventRequest payload) {
        return updateOrderStatus(payload, WAITING_PAYMENT);
    }

    public Mono<Void> handlePaymentCompleted(OrderEventRequest payload) {
        return updateOrderStatus(payload, PAID);
    }

    public Mono<Void> handleOrderCompleted(OrderEventRequest payload) {
        return updateOrderStatus(payload, COMPLETED);
    }


    public Mono<Void> handleRefundCompleted(OrderEventRequest payload) {
        return updateOrderStatus(payload, REFUNDED);
    }

    public Mono<Void> updateOrderStatus(OrderEventRequest payload, AppConstant.ORDER_STATUS orderStatus) {
        return orderRepository.findByTransactionId(payload.getTransactionId())
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Order not found for transactionId: {}", payload.getTransactionId())))
                .flatMap(order -> {
                    order.setOrderStatus(orderStatus.name());
                    if (payload.getFailureCode() != null && !payload.getFailureCode().isEmpty()) {
                        order.setFailureCode(payload.getFailureCode());
                    }
                    if (payload.getFailureMessage() != null && !payload.getFailureMessage().isEmpty()) {
                        order.setFailureMessage(payload.getFailureMessage());
                    }
                    return orderRepository.save(order);
                })
                // record order event to ledger
                .flatMap(orderLedgerService::recordOrderEvent)
                .then();
    }

    public Mono<Void> handleStockOutOfStock(OrderEventRequest payload) {
        return updateOrderStatus(payload, OUT_OF_STOCK);
    }

    public Mono<Void> handleRefundFailed(OrderEventRequest payload) {
        return updateOrderStatus(payload, REFUND_FAILED);
    }

    public Mono<Void> handleOrderExpired(OrderEventRequest payload) {
        return updateOrderStatus(payload, EXPIRED);
    }
}
