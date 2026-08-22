package br.com.nexapay.payment.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;
    private ResilientKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new ResilientKafkaPublisher(kafkaTemplate, meterRegistry, Duration.ofSeconds(1));
    }

    @Test
    void shouldPublishKafkaRecordAndRecordSuccessMetric() {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(KafkaTopics.PAYMENT_CREATED, "payment-1", "{\"status\":\"CREATED\"}");

        when(kafkaTemplate.send(record))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> publisher.send(record));

        verify(kafkaTemplate).send(record);
        assertThat(counter("success")).isEqualTo(1.0);
        assertThat(counter("failure")).isEqualTo(0.0);
    }

    @Test
    void shouldWrapKafkaFailureAndRecordFailureMetric() {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(KafkaTopics.PAYMENT_CREATED, "payment-1", "{\"status\":\"CREATED\"}");

        when(kafkaTemplate.send(record))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        assertThatThrownBy(() -> publisher.send(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to publish Kafka event")
                .hasCauseInstanceOf(java.util.concurrent.ExecutionException.class);

        assertThat(counter("failure")).isEqualTo(1.0);
        assertThat(counter("success")).isEqualTo(0.0);
    }

    private double counter(String result) {
        var counter = meterRegistry.find("nexapay.resilience.kafka.publish.attempts")
                .tags("service", "payment", "instance", "outbox-kafka", "result", result)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
