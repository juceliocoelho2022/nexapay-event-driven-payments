package br.com.nexapay.payment.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
