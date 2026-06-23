package com.MSyamsandiYW.orchestrator_service.config;

import com.MSyamsandiYW.orchestrator_service.kafka.event.OrchestratorCommand;
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

import static com.MSyamsandiYW.orchestrator_service.properties.AppConstant.TOPICS.*;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<String, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<String, Object> senderOptions = SenderOptions.create(kafkaProperties.buildProducerProperties());
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public KafkaReceiver<String, OrchestratorCommand> kafkaReceiver(KafkaProperties kafkaProperties) {
        ReceiverOptions<String, OrchestratorCommand> receiverOptions = ReceiverOptions.
                <String, OrchestratorCommand>create(kafkaProperties.buildConsumerProperties())
                .subscription(List.of(STOCK_RESERVE_COMPLETED, PAYMENT_INITIATED, PAYMENT_COMPLETED, PAYMENT_FAILED,
                        OUT_OF_STOCK, ORDER_REFUND_COMPLETED, ORDER_REFUND_FAILED));
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean
    public NewTopic deductStock() {
        return TopicBuilder.name(DEDUCT_STOCK).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic releaseStock() {
        return TopicBuilder.name(RELEASE_STOCK).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic refundStock() {
        return TopicBuilder.name(REFUND_REQUESTED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCompleted() {
        return TopicBuilder.name(ORDER_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderExpired() {
        return TopicBuilder.name(ORDER_EXPIRED).partitions(3).replicas(1).build();
    }
    @Bean
    public NewTopic orchestratorDlq() {
        return TopicBuilder.name(ORCHESTRATOR_DLQ).partitions(1).replicas(1).build();
    }
}
