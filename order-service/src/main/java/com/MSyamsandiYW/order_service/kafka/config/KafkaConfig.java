package com.MSyamsandiYW.order_service.kafka.config;

import com.MSyamsandiYW.order_service.kafka.request.OrderEventPayload;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.List;

import static com.MSyamsandiYW.order_service.properties.AppConstant.TOPICS.*;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<String, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<String, Object> senderOptions = SenderOptions.create(kafkaProperties.buildProducerProperties());
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public KafkaReceiver<String, OrderEventPayload> kafkaReceiver(KafkaProperties kafkaProperties) {
        ReceiverOptions<String, OrderEventPayload> receiverOptions = ReceiverOptions.<String, OrderEventPayload>create(kafkaProperties
                        .buildConsumerProperties())
                .subscription(List.of(ORDER_COMPLETED, ORDER_FAILED,
                        STOCK_RESERVE_COMPLETED, REFUND_COMPLETED, PAYMENT_COMPLETED));
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean
    public NewTopic reserveStockRequestTopic() {
        return TopicBuilder.name(STOCK_RESERVE_REQUESTED).partitions(3).replicas(1).build();
    }
}
