package com.MSyamsandiYW.order_service.kafka;

import lombok.AllArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Service
@AllArgsConstructor
public class OrderEventProducer {
    private final KafkaSender<String, Object> kafkaSender;

    public Mono<Void> send(
            String topic,
            String key,
            Object payload
    ) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(record, key);
        return kafkaSender.send(Mono.just(senderRecord)).next().then();
    }
}