package br.com.nexapay.payment.service;

import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.event.FraudDecisionMadeEvent;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.repository.ProcessedFraudDecisionEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class FraudDecisionStateMachineService {

    private final PaymentRepository paymentRepository;
    private final ProcessedFraudDecisionEventRepository processedRepository;
    private final MeterRegistry meterRegistry;

    public FraudDecisionStateMachineService(
            PaymentRepository paymentRepository,
            ProcessedFraudDecisionEventRepository processedRepository,
            MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.processedRepository = processedRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public boolean apply(FraudDecisionMadeEvent event) {
        OffsetDateTime processedAt = OffsetDateTime.now();

        int reserved = processedRepository.insertIfAbsent(
                event.eventId(),
                event.fraudDecisionId(),
                event.paymentId(),
                processedAt
        );

        if (reserved == 0) {
            meterRegistry.counter(
                    "nexapay.payment.fraud_decision.duplicate"
            ).increment();
            return false;
        }

        PaymentStatus targetStatus = targetStatus(event.decision());

        int updated = paymentRepository.applyFraudDecision(
                event.paymentId(),
                targetStatus.name(),
                event.decision(),
                event.riskScore(),
                event.reason(),
                event.occurredAt()
        );

        if (updated == 0) {
            meterRegistry.counter(
                    "nexapay.payment.fraud_decision.invalid_state"
            ).increment();

            throw new IllegalStateException(
                    "Fraud decision cannot be applied because payment is not PENDING. paymentId="
                            + event.paymentId()
                            + ", decision="
                            + event.decision()
            );
        }

        meterRegistry.counter(
                "nexapay.payment.fraud_decision.applied",
                "decision",
                event.decision(),
                "target_status",
                targetStatus.name()
        ).increment();

        return true;
    }

    private PaymentStatus targetStatus(String decision) {
        return switch (decision) {
            case "APPROVED" -> PaymentStatus.COMPLETED;
            case "REVIEW" -> PaymentStatus.REVIEW;
            case "BLOCKED" -> PaymentStatus.REJECTED;
            default -> throw new IllegalArgumentException(
                    "Unsupported fraud decision: " + decision
            );
        };
    }
}
