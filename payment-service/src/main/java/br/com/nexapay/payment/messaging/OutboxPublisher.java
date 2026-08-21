package br.com.nexapay.payment.messaging;

import br.com.nexapay.payment.domain.OutboxEvent;
import br.com.nexapay.payment.observability.CorrelationIdFilter;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final ResilientKafkaPublisher resilientKafkaPublisher;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final Propagator propagator;
    private final AtomicInteger pendingBatchSize = new AtomicInteger();

    public OutboxPublisher(
            OutboxEventRepository repository,
            ResilientKafkaPublisher resilientKafkaPublisher,
            MeterRegistry meterRegistry,
            Tracer tracer,
            Propagator propagator) {
        this.repository = repository;
        this.resilientKafkaPublisher = resilientKafkaPublisher;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.propagator = propagator;
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
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {
        String correlationId = event.getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }

        Span publishSpan = restoreParentContext(event)
                .name("outbox publish " + event.getEventType())
                .kind(Span.Kind.PRODUCER)
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(publishSpan)) {
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

            resilientKafkaPublisher.send(record);

            event.markPublished();
            meterRegistry.counter(
                    "nexapay.outbox.published",
                    "service", "payment",
                    "event_type", event.getEventType()
            ).increment();

            log.info(
                    "Outbox publicado. eventId={}, aggregateId={}, traceId={}",
                    event.getId(),
                    event.getAggregateId(),
                    publishSpan.context().traceId()
            );
        } catch (Exception ex) {
            publishSpan.error(ex);
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
        } finally {
            publishSpan.end();
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private Span.Builder restoreParentContext(OutboxEvent event) {
        Map<String, String> carrier = new LinkedHashMap<>();

        if (event.getTraceParent() != null && !event.getTraceParent().isBlank()) {
            carrier.put("traceparent", event.getTraceParent());
        }
        if (event.getTraceState() != null && !event.getTraceState().isBlank()) {
            carrier.put("tracestate", event.getTraceState());
        }

        if (carrier.isEmpty()) {
            return tracer.spanBuilder();
        }

        return propagator.extract(carrier, (source, key) -> source.get(key));
    }
}
