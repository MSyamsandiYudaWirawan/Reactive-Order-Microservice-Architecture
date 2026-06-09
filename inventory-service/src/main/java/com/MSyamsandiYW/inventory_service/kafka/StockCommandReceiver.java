package com.MSyamsandiYW.inventory_service.kafka;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;

import static com.MSyamsandiYW.inventory_service.properties.AppConstant.TOPICS.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockCommandReceiver {
    private final KafkaReceiver<String, StockCommand> kafkaReceiver;
    private final RedisService redisService;
    private final StockCommandHandler handler;

    @PostConstruct
    public void receive() {
        kafkaReceiver.receive()
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
    }

    private Mono<Void> processRecord(ReceiverRecord<String, StockCommand> record) {
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
        Mono<Void> result = switch (record.topic()) {
            case STOCK_RESERVE_REQUESTED -> handler.handleStockReserve(record);
            case RELEASE_STOCK -> handler.handleReleaseStock(record);
            case DEDUCT_STOCK -> handler.handleDeductStock(record);
            default -> Mono.empty();
        };

        return redisService.storeIfAbsent(record.key(), "processed")
                .flatMap(stored -> {
                    if (!stored) {
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
                //acknowledge on matter what
                .doFinally(s -> record.receiverOffset().acknowledge());
    }
}
