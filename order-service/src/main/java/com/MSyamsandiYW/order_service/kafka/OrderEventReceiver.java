package com.MSyamsandiYW.order_service.kafka;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventReceiver {

    private final KafkaReceiver<String, Object> kafkaReceiver;
    private final OrderEventHandler handler;

    @PostConstruct
    public Mono<Void> receive() {
        kafkaReceiver.receive()
                .doOnNext(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
        return Mono.empty();
    }

    private Mono<Void> processRecord(ReceiverRecord<String, Object> record) {
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
//        switch (record.topic()) {
//            case ORDER_COMPLETED -> handler.handleOrderCompleted(record.key(),record.value());
//            case ORDER_FAILED -> handler.handleOrderFailed(record.key(),record.value());
//            case STOCK_RESERVE_COMPLETED -> handler.handleStockReserved(record.key(),record.value());
//            case PAYMENT_COMPLETED -> handler.handlePaymentCompleted(record.key(),record.value());
//            case REFUND_COMPLETED -> handler.handleRefundCompleted(record.key(),record.value());
//        }
        record.receiverOffset().acknowledge();
        return Mono.empty();
    }
}
