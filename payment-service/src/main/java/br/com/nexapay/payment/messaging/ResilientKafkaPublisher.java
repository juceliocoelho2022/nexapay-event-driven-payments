package br.com.nexapay.payment.messaging;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ResilientKafkaPublisher {

    private static final String RESILIENCE_INSTANCE = "outbox-kafka";
    private static final String METRIC_ATTEMPTS = "nexapay.resilience.kafka.publish.attempts";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final Duration publishTimeout;

    public ResilientKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${nexapay.outbox.kafka-publish-timeout:10s}") Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.publishTimeout = publishTimeout;
    }

    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public void send(ProducerRecord<String, String> record) {
        try {
            kafkaTemplate.send(record).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            incrementAttempt("success");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            incrementAttempt("failure");
            throw new IllegalStateException("Interrupted while publishing Kafka event", ex);
        } catch (ExecutionException | TimeoutException ex) {
            incrementAttempt("failure");
            throw new IllegalStateException("Failed to publish Kafka event", ex);
        }
    }

    private void incrementAttempt(String result) {
        meterRegistry.counter(
                METRIC_ATTEMPTS,
                "service", "payment",
                "instance", RESILIENCE_INSTANCE,
                "result", result
        ).increment();
    }
}
