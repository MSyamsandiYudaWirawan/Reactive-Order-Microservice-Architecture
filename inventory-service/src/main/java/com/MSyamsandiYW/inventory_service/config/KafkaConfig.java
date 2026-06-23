package com.MSyamsandiYW.inventory_service.config;

import com.MSyamsandiYW.inventory_service.kafka.event.StockCommand;
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

import static com.MSyamsandiYW.inventory_service.properties.AppConstant.TOPICS.*;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<String, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<String, Object> senderOptions = SenderOptions.create(kafkaProperties.buildProducerProperties());
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public KafkaReceiver<String, StockCommand> kafkaReceiver(KafkaProperties kafkaProperties) {
        ReceiverOptions<String, StockCommand> receiverOptions = ReceiverOptions.<String, StockCommand>create(kafkaProperties.buildConsumerProperties())
                .subscription(List.of(STOCK_RESERVE_REQUESTED, RELEASE_STOCK, DEDUCT_STOCK));
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean
    public NewTopic stockReservedCompletedTopic() {
        return TopicBuilder.name(STOCK_RESERVE_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic outOfStockTopic() {
        return TopicBuilder.name(OUT_OF_STOCK).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryDlqTopic() {
        return TopicBuilder.name(INVENTORY_DLQ).partitions(1).replicas(1).build();
    }

}
