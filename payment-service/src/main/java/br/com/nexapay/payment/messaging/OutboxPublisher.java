package br.com.nexapay.payment.messaging;

import br.com.nexapay.payment.domain.OutboxEvent;
import br.com.nexapay.payment.observability.CorrelationIdFilter;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
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
                .tag("service", "payment")
                .description("Number of unpublished outbox records loaded in the current publisher batch")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${nexapay.outbox.fixed-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events =
                repository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        pendingBatchSize.set(events.size());

        for (OutboxEvent event : events) {
            String correlationId = event.getCorrelationId();

            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
            }

            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        KafkaTopics.PAYMENT_CREATED,
                        event.getAggregateId().toString(),
                        event.getPayload()
                );

                if (correlationId != null && !correlationId.isBlank()) {
                    record.headers().add(new RecordHeader(
                            CorrelationIdFilter.HEADER_NAME,
                            correlationId.getBytes(StandardCharsets.UTF_8)
                    ));
                }

                kafkaTemplate
                        .send(record)
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
            } finally {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            }
        }
    }
}
