package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.ManualFraudReviewAuditResponse;
import br.com.nexapay.payment.api.ManualFraudReviewResponse;
import br.com.nexapay.payment.domain.ManualFraudReviewAudit;
import br.com.nexapay.payment.domain.ManualFraudReviewDecision;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.exception.ManualFraudReviewConflictException;
import br.com.nexapay.payment.exception.PaymentNotFoundException;
import br.com.nexapay.payment.repository.ManualFraudReviewAuditRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ManualFraudReviewService {

    private final PaymentRepository paymentRepository;
    private final ManualFraudReviewAuditRepository auditRepository;
    private final MeterRegistry meterRegistry;

    public ManualFraudReviewService(
            PaymentRepository paymentRepository,
            ManualFraudReviewAuditRepository auditRepository,
            MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.auditRepository = auditRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ManualFraudReviewResponse review(
            UUID paymentId,
            ManualFraudReviewDecision decision,
            String reason,
            String reviewerSubject) {

        OffsetDateTime reviewedAt = OffsetDateTime.now();
        PaymentStatus finalStatus = switch (decision) {
            case APPROVE -> PaymentStatus.COMPLETED;
            case REJECT -> PaymentStatus.REJECTED;
        };

        int updated = paymentRepository.applyManualFraudReview(
                paymentId,
                finalStatus.name(),
                decision.name(),
                reason,
                reviewerSubject,
                reviewedAt
        );

        if (updated == 0) {
            meterRegistry.counter(
                    "nexapay.payment.manual_fraud_review.rejected"
            ).increment();

            if (!paymentRepository.existsById(paymentId)) {
                throw new PaymentNotFoundException(paymentId);
            }

            meterRegistry.counter(
                    "nexapay.payment.manual_fraud_review.concurrent_conflict"
            ).increment();

            throw new ManualFraudReviewConflictException(paymentId);
        }

        auditRepository.save(new ManualFraudReviewAudit(
                UUID.randomUUID(),
                paymentId,
                decision,
                reason,
                reviewerSubject,
                reviewedAt,
                PaymentStatus.REVIEW,
                finalStatus
        ));

        meterRegistry.counter(
                "nexapay.payment.manual_fraud_review.success",
                "decision",
                decision.name(),
                "final_status",
                finalStatus.name()
        ).increment();

        return new ManualFraudReviewResponse(
                paymentId,
                decision,
                PaymentStatus.REVIEW,
                finalStatus,
                reviewerSubject,
                reviewedAt
        );
    }

    @Transactional(readOnly = true)
    public List<ManualFraudReviewAuditResponse> history(UUID paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new PaymentNotFoundException(paymentId);
        }

        return auditRepository.findByPaymentIdOrderByReviewedAtDesc(paymentId)
                .stream()
                .map(audit -> new ManualFraudReviewAuditResponse(
                        audit.getId(),
                        audit.getPaymentId(),
                        audit.getDecision(),
                        audit.getReason(),
                        audit.getReviewerSubject(),
                        audit.getReviewedAt(),
                        audit.getPreviousStatus(),
                        audit.getFinalStatus()
                ))
                .toList();
    }
}
