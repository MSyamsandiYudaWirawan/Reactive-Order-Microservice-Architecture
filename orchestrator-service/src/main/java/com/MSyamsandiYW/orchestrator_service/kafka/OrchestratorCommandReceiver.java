package com.MSyamsandiYW.orchestrator_service.kafka;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.orchestrator_service.kafka.event.DlqEventPayload;
import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorCommand;
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

import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.TOPICS.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrchestratorCommandReceiver {
    private final KafkaReceiver<String, OrchestratorCommand> kafkaReceiver;
    private final OrchestrationCommandHandler handler;
    private final RedisService redisService;
    private final Retry retry;
    private final OrchestratorEventProducer producer;

    @PostConstruct
    public void receiver() {
        kafkaReceiver.receive()
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
    }

    private Mono<Void> processRecord(ReceiverRecord<String, OrchestratorCommand> record) {
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
        // check idempotency eventId as a key, prefixed with service name to avoid conflict with other consumers
        String deduplicationKey = "orchestrator-service:" + record.key();
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
                    return sendToDlq(record,e);
                })
                //acknowledge no matter what
                .doFinally(s -> record.receiverOffset().acknowledge());
    }

    private Mono<Void> processByTopic(ReceiverRecord<String, OrchestratorCommand> record) {
        return switch (record.topic()) {
            case STOCK_RESERVE_COMPLETED -> handler.handleStockReserveCompleted(record.value());
            case PAYMENT_INITIATED -> handler.handlePaymentInitiated(record.value());
            case PAYMENT_COMPLETED -> handler.handlePaymentCompleted(record.value());
            case PAYMENT_FAILED -> handler.handlePaymentFailed(record.value());
            case OUT_OF_STOCK -> handler.handleOutOfStock(record.value());
            case ORDER_REFUND_COMPLETED -> handler.handleOrderRefundCompleted(record.value());
            case ORDER_REFUND_FAILED -> handler.handleOrderRefundFailed(record.value());
            default -> Mono.empty();
        };
    }

    private Mono<Void> sendToDlq(ReceiverRecord<String, OrchestratorCommand> record, Throwable e) {
        // build dlq payload
        DlqEventPayload payload = DlqEventPayload.builder()
                .originalKey(record.key())
                .originalTopic(record.topic())
                .originalPayload(record.value())
                .errorMessage(e.getMessage())
                .timestamp(Instant.now())
                .build();
        //send to dlq topic
        return producer.send(ORCHESTRATOR_DLQ, record.key(), payload)
                .onErrorResume(dlqE -> {
                    log.error("Failed to send to DLQ - topic: {}, key: {}", record.topic(), record.key(), dlqE);
                    return Mono.empty();
                });
    }
}

