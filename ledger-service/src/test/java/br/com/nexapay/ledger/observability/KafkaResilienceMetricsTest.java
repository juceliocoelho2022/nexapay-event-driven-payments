package br.com.nexapay.ledger.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaResilienceMetricsTest {

    @Test
    void shouldRecordFailureRetryAndDltRecoveryMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaResilienceMetrics metrics = new KafkaResilienceMetrics(registry);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("nexapay.account.credited.v1", 0, 10L, "key", "value");

        metrics.failedDelivery(record, new IllegalStateException("db unavailable"), 1);
        metrics.failedDelivery(record, new IllegalStateException("db unavailable"), 2);
        metrics.recovered(record, new IllegalStateException("db unavailable"));

        assertThat(registry.counter(
                "nexapay.kafka.delivery.failures",
                "service", "ledger",
                "topic", record.topic(),
                "exception", "IllegalStateException"
        ).count()).isEqualTo(2.0);

        assertThat(registry.counter(
                "nexapay.kafka.retry.attempts",
                "service", "ledger",
                "topic", record.topic()
        ).count()).isEqualTo(1.0);

        assertThat(registry.counter(
                "nexapay.kafka.dlt.published",
                "service", "ledger",
                "source_topic", record.topic()
        ).count()).isEqualTo(1.0);
    }
}
