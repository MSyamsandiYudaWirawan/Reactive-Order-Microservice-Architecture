package com.MSyamsandiYW.order_service.kafka;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.order_service.kafka.request.OrderEventPayload;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;

import static com.MSyamsandiYW.order_service.properties.AppConstant.TOPICS.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventReceiver {

    private final KafkaReceiver<String, OrderEventPayload> kafkaReceiver;
    private final RedisService redisService;
    private final OrderEventHandler handler;

    @PostConstruct
    public void receive() {
        kafkaReceiver.receive()
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
    }

    private Mono<Void> processRecord(ReceiverRecord<String, OrderEventPayload> record) {
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
        Mono<Void> result = switch (record.topic()) {
            case ORDER_COMPLETED -> handler.handleOrderCompleted(record.value());
            case ORDER_FAILED -> handler.handleOrderFailed(record.value());
            case STOCK_RESERVE_COMPLETED -> handler.handleStockReservedCompleted(record.value());
            case PAYMENT_COMPLETED -> handler.handlePaymentCompleted(record.value());
            case REFUND_COMPLETED -> handler.handleRefundCompleted(record.value());
            default -> Mono.empty();
        };
        // check idempotency eventId as a key
        return redisService.storeIfAbsent(record.key(), "processed")
                .doOnNext(stored -> {
                    if (!stored) log.info("Duplicate event skipped - key: {}", record.key());
                })
                // return mono empty if false
                .filter(stored -> stored)
                // flatmap only run when mono has value or filter is true
                // if there is exception -> retry x times
                .flatMap(stored -> result.retry(3))
                .onErrorResume(e -> {
                    log.error("Failed to process event after retries - topic: {}, key: {}", record.topic(), record.key(), e);
                    return Mono.empty();
                })
                //acknowledge on matter what
                .doFinally(s -> record.receiverOffset().acknowledge());

    }
}
