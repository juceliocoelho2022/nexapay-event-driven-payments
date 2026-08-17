package br.com.nexapay.fraud.config;

import br.com.nexapay.fraud.messaging.InvalidFraudEventPayloadException;
import br.com.nexapay.fraud.observability.KafkaResilienceMetrics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class FraudKafkaResilienceConfig {

    @Bean
    CommonErrorHandler fraudKafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaResilienceMetrics metrics,
            @Value("${nexapay.kafka.resilience.retry-backoff-ms:1000}") long retryBackoffMs,
            @Value("${nexapay.kafka.resilience.max-retries:2}") long maxRetries
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT",
                        record.partition()
                )
        );
        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryBackoffMs, maxRetries)
        );
        errorHandler.addNotRetryableExceptions(InvalidFraudEventPayloadException.class);
        errorHandler.setRetryListeners(metrics);

        return errorHandler;
    }
}
