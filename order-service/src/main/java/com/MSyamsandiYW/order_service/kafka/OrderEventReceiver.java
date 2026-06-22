package com.MSyamsandiYW.order_service.kafka;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.order_service.kafka.event.DlqEventPayload;
import com.MSyamsandiYW.order_service.kafka.event.OrderCommand;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;

import java.time.Instant;

import static com.MSyamsandiYW.order_service.properties.AppConstant.TOPICS.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventReceiver {

    private final KafkaReceiver<String, OrderCommand> kafkaReceiver;
    private final RedisService redisService;
    private final OrderCommandProducer producer;
    private final OrderEventHandler handler;
    private final Retry retry;

    @PostConstruct
    public void receive() {
        kafkaReceiver.receive()
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
    }

    private Mono<Void> processRecord(ReceiverRecord<String, OrderCommand> record) {
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
        // check idempotency eventId as a key, prefixed with service name to avoid conflict with other consumers
        String deduplicationKey = "order-service:" + record.key();
        return redisService.storeIfAbsent(deduplicationKey, "processed")
                .flatMap(stored -> {
                    if (!stored) {
                        log.info("Duplicate event skipped - key: {}", deduplicationKey);
                        // skip if already processed
                        return Mono.empty();
                    }
                    return Mono.just(true);
                })
                //if mono is empty the flatmap will skipped
                .flatMap(stored -> processByTopic(record)
                        //add reactor retry resilience4j
                        .transformDeferred(RetryOperator.of(retry)))
                .onErrorResume(e -> {
                    log.error("Failed to process event after retries - topic: {}, key: {}", record.topic(), record.key(), e);
                    return sendToDlq(record, e);
                })
                //acknowledge no matter what
                .doFinally(s -> record.receiverOffset().acknowledge());

    }

    private Mono<Void> sendToDlq(ReceiverRecord<String, OrderCommand> record, Throwable error) {
        // build dlq payload
        DlqEventPayload payload = DlqEventPayload.builder()
                .originalTopic(record.topic())
                .originalKey(record.key())
                .originalPayload(record.value())
                .errorMessage(error.getMessage())
                .timestamp(Instant.now())
                .build();

        //send to dlq topic
        return producer.send(ORDER_DLQ, record.key(), payload)
                .onErrorResume(dlqE -> {
                    log.error("Failed to send to DLQ - topic: {}, key: {}", record.topic(), record.key(), dlqE);
                    return Mono.empty();
                });
    }

    private Mono<Void> processByTopic(ReceiverRecord<String, OrderCommand> record) {
        return switch (record.topic()) {

            case STOCK_RESERVE_COMPLETED -> handler.handleStockReservedCompleted(record.value());
            case OUT_OF_STOCK -> handler.handleStockOutOfStock(record.value());
            case PAYMENT_COMPLETED -> handler.handlePaymentCompleted(record.value());
            case ORDER_COMPLETED -> handler.handleOrderCompleted(record.value());
            case REFUND_COMPLETED -> handler.handleRefundCompleted(record.value());
            case REFUND_FAILED -> handler.handleRefundFailed(record.value());
            case ORDER_EXPIRED -> handler.handleOrderExpired(record.value());
            default -> Mono.empty();
        };
    }
}
