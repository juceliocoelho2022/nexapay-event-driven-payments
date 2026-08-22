package br.com.nexapay.payment.messaging;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaChaosRecoveryTest {

    @Test
    void shouldOpenCircuitDuringKafkaOutageAndCloseAfterRecovery() throws Exception {
        AtomicBoolean kafkaAvailable = new AtomicBoolean(true);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            if (kafkaAvailable.get()) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("simulated Kafka outage"));
        });

        ResilientKafkaPublisher publisher = new ResilientKafkaPublisher(
                kafkaTemplate,
                meterRegistry,
                Duration.ofMillis(250)
        );

        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "outbox-kafka-chaos-test",
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(3)
                        .minimumNumberOfCalls(3)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofMillis(100))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .recordException(IllegalStateException.class)
                        .build()
        );

        Retry retry = Retry.of(
                "outbox-kafka-chaos-test",
                RetryConfig.custom()
                        .maxAttempts(2)
                        .waitDuration(Duration.ofMillis(10))
                        .retryExceptions(IllegalStateException.class)
                        .build()
        );

        ProducerRecord<String, String> healthyRecord = record("healthy");
        runResiliently(circuitBreaker, retry, () -> publisher.send(healthyRecord));

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(counter(meterRegistry, "success")).isEqualTo(1.0);

        kafkaAvailable.set(false);

        assertThatThrownBy(() -> runResiliently(circuitBreaker, retry, () -> publisher.send(record("outage"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(counter(meterRegistry, "failure")).isEqualTo(2.0);

        Thread.sleep(200);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        kafkaAvailable.set(true);

        runResiliently(circuitBreaker, retry, () -> publisher.send(record("recovery-1")));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        runResiliently(circuitBreaker, retry, () -> publisher.send(record("recovery-2")));

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(counter(meterRegistry, "success")).isEqualTo(3.0);
        assertThat(counter(meterRegistry, "failure")).isEqualTo(2.0);
    }

    private static void runResiliently(CircuitBreaker circuitBreaker, Retry retry, Runnable action) {
        Runnable protectedAction = CircuitBreaker.decorateRunnable(circuitBreaker, action);
        Retry.decorateRunnable(retry, protectedAction).run();
    }

    private static ProducerRecord<String, String> record(String key) {
        return new ProducerRecord<>(
                KafkaTopics.PAYMENT_CREATED,
                key,
                "{\"test\":\"" + key + "\"}"
        );
    }

    private static double counter(SimpleMeterRegistry registry, String result) {
        var counter = registry.find("nexapay.resilience.kafka.publish.attempts")
                .tags("service", "payment", "instance", "outbox-kafka", "result", result)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
