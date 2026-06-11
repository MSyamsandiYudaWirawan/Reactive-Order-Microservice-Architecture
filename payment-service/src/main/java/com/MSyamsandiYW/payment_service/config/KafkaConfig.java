package com.MSyamsandiYW.payment_service.config;

import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
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

import static com.MSyamsandiYW.payment_service.properties.AppConstant.TOPICS.REFUND_REQUESTED;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaSender<String, Object> kafkaSender(KafkaProperties kafkaProperties) {
        SenderOptions<String, Object> senderOptions = SenderOptions.create(kafkaProperties.buildProducerProperties());
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public KafkaReceiver<String, PaymentCommand> kafkaReceiver(KafkaProperties kafkaProperties) {
        ReceiverOptions<String, PaymentCommand> receiverOptions = ReceiverOptions.<String, PaymentCommand>create(kafkaProperties.buildConsumerProperties())
                .subscription(List.of(REFUND_REQUESTED));
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean
    public NewTopic paymentCompleteTopic() {
        return TopicBuilder.name("payment-completed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name("payment-failed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderRefundCompleted() {
        return TopicBuilder.name("order-refund-completed").partitions(3).replicas(1).build();
    }

}
