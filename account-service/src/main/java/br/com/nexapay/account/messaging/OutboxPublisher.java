package br.com.nexapay.account.messaging;

import br.com.nexapay.account.domain.OutboxEvent;
import br.com.nexapay.account.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "nexapay.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger pendingBatchSize = new AtomicInteger();

    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        Gauge.builder("nexapay.outbox.batch.pending", pendingBatchSize, AtomicInteger::get)
                .tag("service", "account")
                .description("Number of unpublished outbox records loaded in the current publisher batch")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${nexapay.outbox.fixed-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = repository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        pendingBatchSize.set(events.size());

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate
                        .send(topicFor(event.getEventType()), event.getAggregateId().toString(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);

                event.markPublished();
                meterRegistry.counter(
                        "nexapay.outbox.published",
                        "service", "account",
                        "event_type", event.getEventType()
                ).increment();

                log.info("Account outbox published. eventId={}, aggregateId={}, eventType={}",
                        event.getId(), event.getAggregateId(), event.getEventType());
            } catch (Exception exception) {
                meterRegistry.counter(
                        "nexapay.outbox.publish.failures",
                        "service", "account",
                        "event_type", event.getEventType()
                ).increment();
                log.error("Failed to publish account outbox event. eventId={}, eventType={}",
                        event.getId(), event.getEventType(), exception);
                break;
            }
        }
    }

    private String topicFor(String eventType) {
        return switch (eventType) {
            case "ACCOUNT_CREDITED" -> KafkaTopics.ACCOUNT_CREDITED;
            case "ACCOUNT_DEBITED" -> KafkaTopics.ACCOUNT_DEBITED;
            default -> throw new IllegalArgumentException("Unsupported account event type: " + eventType);
        };
    }
}
