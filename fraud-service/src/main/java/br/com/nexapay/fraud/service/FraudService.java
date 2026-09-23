package br.com.nexapay.fraud.service;

import br.com.nexapay.fraud.domain.FraudDecision;
import br.com.nexapay.fraud.domain.FraudOutboxEvent;
import br.com.nexapay.fraud.event.FraudDecisionMadeEvent;
import br.com.nexapay.fraud.event.PaymentCreatedEvent;
import br.com.nexapay.fraud.repository.FraudDecisionRepository;
import br.com.nexapay.fraud.repository.FraudOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class FraudService {

    private static final Logger log = LoggerFactory.getLogger(FraudService.class);
    private static final String CORRELATION_MDC = "correlationId";

    private final FraudDecisionRepository repository;
    private final FraudRuleEngine ruleEngine;
    private final MeterRegistry meterRegistry;
    private final FraudOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Propagator propagator;

    public FraudService(
            FraudDecisionRepository repository,
            FraudRuleEngine ruleEngine,
            MeterRegistry meterRegistry,
            FraudOutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            Tracer tracer,
            Propagator propagator
    ) {
        this.repository = repository;
        this.ruleEngine = ruleEngine;
        this.meterRegistry = meterRegistry;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Transactional
    public FraudDecision analyze(PaymentCreatedEvent event) {
        Optional<FraudDecision> existing = repository.findByEventId(event.eventId());

        if (existing.isPresent()) {
            meterRegistry.counter("nexapay.fraud.duplicates", "source", "precheck").increment();
            log.info("Ignoring duplicated fraud event. eventId={}", event.eventId());
            return existing.get();
        }

        FraudRuleEngine.RiskAssessment assessment = ruleEngine.assess(event.amount());

        FraudDecision decision = FraudDecision.create(
                event.eventId(),
                event.paymentId(),
                event.payerAccountId(),
                event.pixKey(),
                event.amount(),
                assessment.decision(),
                assessment.riskScore(),
                assessment.reason(),
                event.occurredAt()
        );

        int inserted = repository.insertIfEventAbsent(
                decision.getId(),
                decision.getEventId(),
                decision.getPaymentId(),
                decision.getPayerAccountId(),
                decision.getPixKey(),
                decision.getAmount(),
                decision.getDecision().name(),
                decision.getRiskScore(),
                decision.getReason(),
                decision.getOccurredAt(),
                decision.getAnalyzedAt()
        );

        if (inserted == 0) {
            meterRegistry.counter("nexapay.fraud.duplicates", "source", "concurrent_conflict").increment();
            FraudDecision winner = repository.findByEventId(event.eventId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Fraud event was claimed but decision could not be loaded: " + event.eventId()
                    ));
            log.info("Ignoring duplicated fraud event after concurrent insert. eventId={}", event.eventId());
            return winner;
        }

        outboxRepository.save(buildDecisionOutbox(decision));
        meterRegistry.counter("nexapay.fraud.outbox.created").increment();

        meterRegistry.counter(
                "nexapay.fraud.decisions",
                "decision", decision.getDecision().name()
        ).increment();

        log.info(
                "Fraud analysis completed. paymentId={}, decision={}, riskScore={}",
                decision.getPaymentId(),
                decision.getDecision(),
                decision.getRiskScore()
        );

        return decision;
    }

    @Transactional(readOnly = true)
    public Optional<FraudDecision> findByPaymentId(UUID paymentId) {
        return repository.findByPaymentId(paymentId);
    }

    private FraudOutboxEvent buildDecisionOutbox(FraudDecision decision) {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = decision.getAnalyzedAt();

        FraudDecisionMadeEvent event = new FraudDecisionMadeEvent(
                eventId,
                decision.getId(),
                decision.getPaymentId(),
                decision.getDecision(),
                decision.getRiskScore(),
                decision.getReason(),
                occurredAt
        );

        String correlationId = MDC.get(CORRELATION_MDC);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = eventId.toString();
        }

        Map<String, String> traceContext = captureTraceContext();

        return new FraudOutboxEvent(
                eventId,
                decision.getPaymentId(),
                "FraudDecisionMade",
                toJson(event),
                correlationId,
                traceContext.get("traceparent"),
                traceContext.get("tracestate"),
                false,
                occurredAt
        );
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

    private String toJson(FraudDecisionMadeEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize FraudDecisionMade event",
                    exception
            );
        }
    }
}
