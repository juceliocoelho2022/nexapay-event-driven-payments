package br.com.nexapay.fraud.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaResilienceMetrics implements RetryListener {

    private final MeterRegistry meterRegistry;

    public KafkaResilienceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int deliveryAttempt) {
        meterRegistry.counter(
                "nexapay.kafka.delivery.failures",
                "service", "fraud",
                "topic", record.topic(),
                "exception", exceptionName(exception)
        ).increment();

        if (deliveryAttempt > 1) {
            meterRegistry.counter(
                    "nexapay.kafka.retry.attempts",
                    "service", "fraud",
                    "topic", record.topic()
            ).increment();
        }
    }

    @Override
    public void recovered(ConsumerRecord<?, ?> record, Exception exception) {
        meterRegistry.counter(
                "nexapay.kafka.dlt.published",
                "service", "fraud",
                "source_topic", record.topic()
        ).increment();
    }

    @Override
    public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
        meterRegistry.counter(
                "nexapay.kafka.dlt.publish.failures",
                "service", "fraud",
                "source_topic", record.topic(),
                "exception", exceptionName(failure)
        ).increment();
    }

    private String exceptionName(Exception exception) {
        return exception == null ? "unknown" : exception.getClass().getSimpleName();
    }
}
