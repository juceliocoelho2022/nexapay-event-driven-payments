package br.com.nexapay.payment.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientKafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ResilientKafkaPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ResilientKafkaPublisher(kafkaTemplate);
    }

    @Test
    void shouldPublishKafkaRecord() {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(KafkaTopics.PAYMENT_CREATED, "payment-1", "{\"status\":\"CREATED\"}");

        when(kafkaTemplate.send(record))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> publisher.send(record));

        verify(kafkaTemplate).send(record);
    }

    @Test
    void shouldWrapKafkaFailureAsRetryableException() {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(KafkaTopics.PAYMENT_CREATED, "payment-1", "{\"status\":\"CREATED\"}");

        when(kafkaTemplate.send(record))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        assertThatThrownBy(() -> publisher.send(record))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to publish Kafka event")
                .hasCauseInstanceOf(java.util.concurrent.ExecutionException.class);
    }
}
