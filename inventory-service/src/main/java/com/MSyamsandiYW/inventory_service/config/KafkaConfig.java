package com.MSyamsandiYW.inventory_service.config;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.List;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<String, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<String, Object> senderOptions = SenderOptions.create(kafkaProperties.buildProducerProperties());
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public KafkaReceiver<String, Object> kafkaReceiver(KafkaProperties kafkaProperties) {
        ReceiverOptions<String, Object> receiverOptions = ReceiverOptions.create(kafkaProperties.buildConsumerProperties())
                .subscription(List.of());
        return KafkaReceiver.create(receiverOptions);
    }
}
