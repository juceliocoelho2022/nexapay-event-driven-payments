package br.com.nexapay.payment.messaging;

import br.com.nexapay.payment.domain.OutboxEvent;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${nexapay.outbox.fixed-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events =
                repository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        meterRegistry.gauge("nexapay.outbox.batch.pending", events, List::size);

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate
                        .send(
                                KafkaTopics.PAYMENT_CREATED,
                                event.getAggregateId().toString(),
                                event.getPayload()
                        )
                        .get(10, TimeUnit.SECONDS);

                event.markPublished();
                meterRegistry.counter(
                        "nexapay.outbox.published",
                        "service", "payment",
                        "event_type", event.getEventType()
                ).increment();

                log.info(
                        "Outbox publicado. eventId={}, aggregateId={}",
                        event.getId(),
                        event.getAggregateId()
                );
            } catch (Exception ex) {
                meterRegistry.counter(
                        "nexapay.outbox.publish.failures",
                        "service", "payment",
                        "event_type", event.getEventType()
                ).increment();
                log.error(
                        "Falha ao publicar evento outbox. eventId={}",
                        event.getId(),
                        ex
                );
                break;
            }
        }
    }
}
