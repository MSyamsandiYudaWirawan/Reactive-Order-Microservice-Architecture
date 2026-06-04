package com.MSyamsandiYW.order_service.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderEventHandler {

    private Mono<Void> handleStockReserved(Object payload) {
        // stock reserved → update order status to WAITING_PAYMENT
        return Mono.empty();
    }

    private Mono<Void> handlePaymentCompleted(Object payload) {
        // payment done → update order status to PAID
        return Mono.empty();
    }

    private Mono<Void> handleOrderCompleted(Object payload) {
        // fulfillment success → update order status to COMPLETED
        return Mono.empty();
    }

    private Mono<Void> handleOrderFailed(Object payload) {
        // something failed → update order status to FAILED\
        return Mono.empty();
    }

    private Mono<Void> handleRefundCompleted(Object payload) {
        // refund done → update order status to REFUNDED
        return Mono.empty();
    }
}
