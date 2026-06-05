package com.MSyamsandiYW.order_service.kafka;

import com.MSyamsandiYW.order_service.kafka.request.OrderEventPayload;
import com.MSyamsandiYW.order_service.order.OrderRepository;
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

    public Mono<Void> handleStockReservedCompleted(OrderEventPayload payload) {
        return updateOrderStatus(payload.getTransactionId(), WAITING_PAYMENT);
    }

    public Mono<Void> handlePaymentCompleted(OrderEventPayload payload) {
        return updateOrderStatus(payload.getTransactionId(), PAID);
    }

    public Mono<Void> handleOrderCompleted(OrderEventPayload payload) {
        return updateOrderStatus(payload.getTransactionId(), COMPLETED);
    }

    public Mono<Void> handleOrderFailed(OrderEventPayload payload) {
        return updateOrderStatus(payload.getTransactionId(), FAILED);
    }

    public Mono<Void> handleRefundCompleted(OrderEventPayload payload) {
        return updateOrderStatus(payload.getTransactionId(), REFUNDED);
    }

    public Mono<Void> updateOrderStatus(String transactionId, AppConstant.ORDER_STATUS orderStatus) {
        return orderRepository.findByTransactionId(transactionId)
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Order not found for transactionId: {}", transactionId)))
                .flatMap(order -> {
                    order.setOrderStatus(orderStatus.name());
                    return orderRepository.save(order);
                })
                .then();
    }
}
