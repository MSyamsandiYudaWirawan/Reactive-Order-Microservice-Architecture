package com.MSyamsandiYW.payment_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventProducer {
    private final KafkaSender<String,Object> kafkaSender;

    public Mono<Void> send(
            String topic,
            String key,
            Object payload
    ){
        log.info("Publish event - topic: {}, key: {}, value: {}", topic, key, payload);
        ProducerRecord<String,Object> producerRecord = new ProducerRecord<>(topic,key,payload);
        SenderRecord<String,Object,String> senderRecord = SenderRecord.create(producerRecord,key);
        return kafkaSender.send(Mono.just(senderRecord)).next().then();
    }
}
