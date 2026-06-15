package com.MSyamsandiYW.order_service.kafka;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.order_service.kafka.request.OrderEventRequest;
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

    private final KafkaReceiver<String, OrderEventRequest> kafkaReceiver;
    private final RedisService redisService;
    private final OrderEventHandler handler;

    @PostConstruct
    public void receive() {
        kafkaReceiver.receive()
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
    }

    private Mono<Void> processRecord(ReceiverRecord<String, OrderEventRequest> record) {
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
        Mono<Void> result = switch (record.topic()) {

            case STOCK_RESERVE_COMPLETED -> handler.handleStockReservedCompleted(record.value());
            case OUT_OF_STOCK -> handler.handleStockOutOfStock(record.value());
            case PAYMENT_COMPLETED -> handler.handlePaymentCompleted(record.value());
            case ORDER_COMPLETED -> handler.handleOrderCompleted(record.value());
            case REFUND_COMPLETED -> handler.handleRefundCompleted(record.value());
            case REFUND_FAILED -> handler.handleRefundFailed(record.value());
            case ORDER_EXPIRED -> handler.handleOrderExpired(record.value());
            default -> Mono.empty();
        };
        // check idempotency eventId as a key
        return redisService.storeIfAbsent(record.key(), "processed")
                .flatMap(stored -> {
                    if(!stored){
                        log.info("Duplicate event skipped - key: {}", record.key());
                        // skip if already processed
                        return Mono.empty();
                    }
                    return Mono.just(true);
                })
                //if mono is empty the flatmap will skipped
                .flatMap(stored -> result.retry(3))
                .onErrorResume(e -> {
                    log.error("Failed to process event after retries - topic: {}, key: {}", record.topic(), record.key(), e);
                    return Mono.empty();
                })
                //acknowledge no matter what
                .doFinally(s -> record.receiverOffset().acknowledge());

    }
}
