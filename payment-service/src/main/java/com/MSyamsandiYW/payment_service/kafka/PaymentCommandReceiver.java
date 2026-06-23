package com.MSyamsandiYW.payment_service.kafka;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.payment_service.kafka.event.DlqEventPayload;
import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
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

import static com.MSyamsandiYW.payment_service.properties.AppConstant.TOPICS.PAYMENT_DLQ;
import static com.MSyamsandiYW.payment_service.properties.AppConstant.TOPICS.REFUND_REQUESTED;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCommandReceiver {
    private final KafkaReceiver<String, PaymentCommand> kafkaReceiver;
    private final PaymentCommandHandler handler;
    private final RedisService redisService;
    private final Retry retry;
    private final PaymentEventProducer producer;

    @PostConstruct
    public void receive() {
        kafkaReceiver.receive()
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
    }

    public Mono<Void> processRecord(ReceiverRecord<String, PaymentCommand> record) {
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
        // check idempotency eventId as a key, prefixed with service name to avoid conflict with other consumers
        String deduplicationKey = "payment-service:" + record.key();
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
                        //add retry reactor resilience4j
                        .transformDeferred(RetryOperator.of(retry)))
                .onErrorResume(e -> {
                    log.error("Failed to process event after retries - topic: {}, key: {}", record.topic(), record.key(), e);
                    return sendToDlq(record, e);
                })
                //acknowledge no matter what
                .doFinally(s -> record.receiverOffset().acknowledge());
    }

    private Mono<Void> processByTopic(ReceiverRecord<String, PaymentCommand> record) {
        return switch (record.topic()) {
            case REFUND_REQUESTED -> handler.handleRefundPayment(record.value());
            default -> Mono.empty();
        };
    }

    private Mono<Void> sendToDlq(ReceiverRecord<String, PaymentCommand> record, Throwable e) {
        //build dlq payload
        DlqEventPayload payload = DlqEventPayload.builder()
                .originalKey(record.key())
                .originalTopic(record.topic())
                .originalPayload(record.value())
                .errorMessage(e.getMessage())
                .timestamp(Instant.now())
                .build();
        //send to dlq topic
        return producer.send(PAYMENT_DLQ, record.key(), payload)
                .onErrorResume(dlqE -> {
                    log.error("Failed to send to DLQ - topic: {}, key: {}", record.topic(), record.key(), dlqE);
                    return Mono.empty();
                });
    }
}
