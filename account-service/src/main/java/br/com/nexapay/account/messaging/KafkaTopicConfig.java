package br.com.nexapay.account.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(prefix = "nexapay.kafka", name = "manage-topics", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    public NewTopic accountCreditedTopic() {
        return TopicBuilder.name(KafkaTopics.ACCOUNT_CREDITED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic accountDebitedTopic() {
        return TopicBuilder.name(KafkaTopics.ACCOUNT_DEBITED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
