package br.com.nexapay.fraud.messaging;

import br.com.nexapay.fraud.domain.FraudOutboxEvent;
import br.com.nexapay.fraud.repository.FraudOutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class FraudOutboxPublisher {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_MDC = "correlationId";

    private final FraudOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final Propagator propagator;
    private final Duration publishTimeout;

    public FraudOutboxPublisher(
            FraudOutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            Tracer tracer,
            Propagator propagator,
            @Value("${nexapay.fraud.outbox.kafka-publish-timeout:10s}")
            Duration publishTimeout) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.propagator = propagator;
        this.publishTimeout = publishTimeout;
    }

    @Scheduled(
            fixedDelayString = "${nexapay.fraud.outbox.fixed-delay-ms:2000}",
            initialDelayString = "${nexapay.fraud.outbox.initial-delay-ms:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<FraudOutboxEvent> events =
                repository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        for (FraudOutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(FraudOutboxEvent event) {
        String correlationId = event.getCorrelationId();

        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CORRELATION_MDC, correlationId);
        }

        Span span = restoreParentContext(event)
                .name("outbox publish " + event.getEventType())
                .kind(Span.Kind.PRODUCER)
                .start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    FraudKafkaTopics.FRAUD_DECISION_MADE,
                    event.getAggregateId().toString(),
                    event.getPayload()
            );

            if (correlationId != null && !correlationId.isBlank()) {
                record.headers().add(new RecordHeader(
                        CORRELATION_HEADER,
                        correlationId.getBytes(StandardCharsets.UTF_8)
                ));
            }

            kafkaTemplate.send(record)
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);

            event.markPublished();
            meterRegistry.counter("nexapay.fraud.outbox.published").increment();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            span.error(exception);
            meterRegistry.counter("nexapay.fraud.outbox.publish.failures")
                    .increment();
        } catch (Exception exception) {
            span.error(exception);
            meterRegistry.counter("nexapay.fraud.outbox.publish.failures")
                    .increment();
        } finally {
            span.end();
            MDC.remove(CORRELATION_MDC);
        }
    }

    private Span.Builder restoreParentContext(FraudOutboxEvent event) {
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

        return propagator.extract(
                carrier,
                (source, key) -> source.get(key)
        );
    }
}
