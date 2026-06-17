package com.MSyamsandiYW.payment_service.kafka;

import com.MSyamsandiYW.common.redis.RedisService;
import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;

import static com.MSyamsandiYW.payment_service.properties.AppConstant.TOPICS.REFUND_REQUESTED;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCommandReceiver {
    private final KafkaReceiver<String, PaymentCommand> kafkaReceiver;
    private final PaymentCommandHandler handler;
    private final RedisService redisService;

    @PostConstruct
    public void receive(){
        kafkaReceiver.receive()
                .flatMap(this::processRecord)
                .doOnError(e -> log.error("Error receiving event", e))
                .subscribe();
    }

    public Mono<Void> processRecord(ReceiverRecord<String, PaymentCommand> record){
        log.info("Received event - topic: {}, key: {}, value: {}", record.topic(), record.key(), record.value());
        Mono<Void> result = switch (record.topic()) {
            case REFUND_REQUESTED -> handler.handleRefundPayment(record.value());
            default -> Mono.empty();
        };
        // check idempotency eventId as a key, prefixed with service name to avoid conflict with other consumers
        String deduplicationKey = "payment-service:" + record.key();
        return redisService.storeIfAbsent(deduplicationKey, "processed")
                .flatMap(stored -> {
                    if(!stored){
                        log.info("Duplicate event skipped - key: {}", deduplicationKey);
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
