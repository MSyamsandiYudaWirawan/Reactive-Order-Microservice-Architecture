package com.MSyamsandiYW.payment_service.config;

import com.MSyamsandiYW.payment_service.kafka.event.PaymentCommand;
import com.MSyamsandiYW.payment_service.properties.AppConstant;
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

import static com.MSyamsandiYW.payment_service.properties.AppConstant.TOPICS.*;

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
        return TopicBuilder.name(PAYMENT_COMPLETED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(PAYMENT_FAILED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderRefundCompleted() {
        return TopicBuilder.name(ORDER_REFUND_COMPLETED).partitions(3).replicas(1).build();
    }
    @Bean
    public NewTopic paymentInitiated() {
        return TopicBuilder.name(PAYMENT_INITIATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentDlqTopic() {
        return TopicBuilder.name(PAYMENT_DLQ).partitions(1).replicas(1).build();
    }

}
