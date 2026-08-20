package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.CreatePixPaymentRequest;
import br.com.nexapay.payment.api.PaymentResponse;
import br.com.nexapay.payment.domain.OutboxEvent;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.event.PaymentCreatedEvent;
import br.com.nexapay.payment.exception.PaymentNotFoundException;
import br.com.nexapay.payment.observability.CorrelationIdFilter;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final Propagator propagator;

    public PaymentService(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            PaymentMapper paymentMapper,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            Tracer tracer,
            Propagator propagator) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentMapper = paymentMapper;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Transactional
    public PaymentResponse createPixPayment(
            String idempotencyKey,
            CreatePixPaymentRequest request) {

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    meterRegistry.counter("nexapay.payment.idempotency.reused", "source", "precheck").increment();
                    return paymentMapper.toResponse(existing);
                })
                .orElseGet(() -> createOrLoadPayment(idempotencyKey, request));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        return paymentMapper.toResponse(payment);
    }

    private PaymentResponse createOrLoadPayment(
            String idempotencyKey,
            CreatePixPaymentRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Payment payment = new Payment(
                paymentId,
                idempotencyKey,
                request.payerAccountId(),
                request.pixKey(),
                request.amount(),
                request.description(),
                PaymentStatus.PENDING,
                now
        );

        int inserted = paymentRepository.insertIfIdempotencyKeyAbsent(
                paymentId,
                idempotencyKey,
                request.payerAccountId(),
                request.pixKey(),
                request.amount(),
                request.description(),
                PaymentStatus.PENDING.name(),
                now
        );

        if (inserted == 0) {
            meterRegistry.counter("nexapay.payment.idempotency.reused", "source", "concurrent_conflict").increment();
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .map(paymentMapper::toResponse)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency key was claimed but payment could not be loaded: " + idempotencyKey
                    ));
        }

        PaymentCreatedEvent event = new PaymentCreatedEvent(
                eventId,
                paymentId,
                request.payerAccountId(),
                request.pixKey(),
                request.amount(),
                now
        );

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = eventId.toString();
        }

        Map<String, String> traceContext = captureTraceContext();

        OutboxEvent outbox = new OutboxEvent(
                eventId,
                paymentId,
                "PaymentCreated",
                toJson(event),
                correlationId,
                traceContext.get("traceparent"),
                traceContext.get("tracestate"),
                false,
                now
        );

        outboxEventRepository.save(outbox);
        meterRegistry.counter("nexapay.payment.created").increment();

        return paymentMapper.toResponse(payment);
    }

    private Map<String, String> captureTraceContext() {
        Map<String, String> carrier = new LinkedHashMap<>();
        Span currentSpan = tracer.currentSpan();

        if (currentSpan != null) {
            propagator.inject(
                    currentSpan.context(),
                    carrier,
                    (target, key, value) -> target.put(key, value)
            );
        }

        return carrier;
    }

    private String toJson(PaymentCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento de pagamento", e);
        }
    }
}
