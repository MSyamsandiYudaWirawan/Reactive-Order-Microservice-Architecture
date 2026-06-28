package com.MSyamsandiYW.inventory_service.kafka;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.inventory_service.kafka.event.DlqEventPayload;
import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
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

import static com.MSyamsandiYW.inventory_service.properties.AppConstant.TOPICS.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockCommandReceiver {
    private final KafkaReceiver<String, StockCommand> kafkaReceiver;
    private final RedisService redisService;
    private final StockCommandHandler handler;
    private final StockEventProducer producer;
    private final Retry retry;

    @PostConstruct
    public void receive() {
        kafkaReceiver.receive()
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
    }

    private Mono<Void> processRecord(ReceiverRecord<String, StockCommand> record) {
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
        // prefixed with service name to avoid conflict with other consumers
        String deduplicationKey = "inventory-service:" + record.key();
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
                .flatMap(stored -> processByTopic(record).transformDeferred(RetryOperator.of(retry)))
                .onErrorResume(e -> {
                    log.error("Failed to process event after retries - topic: {}, key: {}", record.topic(), record.key(), e);
                    return sendToDql(record, e);
                })
                //acknowledge no matter what
                .doFinally(s -> record.receiverOffset().acknowledge());
    }


    private Mono<Void> sendToDql(ReceiverRecord<String, StockCommand> record, Throwable error) {
        // Create a DLQ envelope with metadata
        DlqEventPayload payload = DlqEventPayload.builder()
                .originalTopic(record.topic())
                .originalKey(record.key())
                .originalPayload(record.value())
                .errorMessage(error.getMessage())
                .timestamp(Instant.now())
                .build();
        // Publish to DLQ topic
        return producer.send(INVENTORY_DLQ, record.key(), payload)
                .onErrorResume(dlqE -> {
                    log.error("Failed to send to DLQ - topic: {}, key: {}", record.topic(), record.key(), dlqE);
                    return Mono.empty();
                });
    }

    private Mono<Void> processByTopic(ReceiverRecord<String, StockCommand> record) {
        return switch (record.topic()) {
            case STOCK_RESERVE_REQUESTED -> handler.handleStockReserve(record);
            case RELEASE_STOCK -> handler.handleReleaseStock(record);
            case DEDUCT_STOCK -> handler.handleDeductStock(record);
            default -> Mono.empty();
        };
    }
}
