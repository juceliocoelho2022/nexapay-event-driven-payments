package br.com.nexapay.payment.messaging;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ResilientKafkaPublisher {

    private static final String RESILIENCE_INSTANCE = "outbox-kafka";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public ResilientKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Retry(name = RESILIENCE_INSTANCE)
    @CircuitBreaker(name = RESILIENCE_INSTANCE)
    public void send(ProducerRecord<String, String> record) {
        try {
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing Kafka event", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Failed to publish Kafka event", ex);
        }
    }
}
