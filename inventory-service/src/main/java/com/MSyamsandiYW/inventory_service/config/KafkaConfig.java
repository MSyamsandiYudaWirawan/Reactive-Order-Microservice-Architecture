package com.MSyamsandiYW.inventory_service.config;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.List;

import static com.MSyamsandiYW.inventory_service.properties.AppConstant.TOPICS.*;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<String, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<String, Object> senderOptions = SenderOptions.create(kafkaProperties.buildProducerProperties());
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public KafkaReceiver<String, Object> kafkaReceiver(KafkaProperties kafkaProperties) {
        ReceiverOptions<String, Object> receiverOptions = ReceiverOptions.<String, Object>create(kafkaProperties.buildConsumerProperties())
                .subscription(List.of(STOCK_RESERVE_REQUESTED.name(), RELEASE_STOCK.name(), DEDUCT_STOCK.name()));
        return KafkaReceiver.create(receiverOptions);
    }
}
