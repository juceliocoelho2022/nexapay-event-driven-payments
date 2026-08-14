package br.com.nexapay.account.messaging;

import br.com.nexapay.account.domain.OutboxEvent;
import br.com.nexapay.account.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "nexapay.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${nexapay.outbox.fixed-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = repository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate
                        .send(topicFor(event.getEventType()), event.getAggregateId().toString(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);

                event.markPublished();

                log.info("Account outbox published. eventId={}, aggregateId={}, eventType={}",
                        event.getId(), event.getAggregateId(), event.getEventType());
            } catch (Exception exception) {
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
