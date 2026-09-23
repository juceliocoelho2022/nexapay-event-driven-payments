package br.com.nexapay.fraud.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class FraudKafkaTopicConfig {

    @Bean
    NewTopic fraudDecisionMadeTopic() {
        return TopicBuilder.name(FraudKafkaTopics.FRAUD_DECISION_MADE)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
