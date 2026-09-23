package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.FraudReviewCaseResponse;
import br.com.nexapay.payment.domain.FraudReviewCase;
import br.com.nexapay.payment.domain.FraudReviewPriority;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.exception.FraudReviewCaseConflictException;
import br.com.nexapay.payment.exception.FraudReviewCaseNotFoundException;
import br.com.nexapay.payment.repository.FraudReviewCaseRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FraudReviewQueueService {

    private final FraudReviewCaseRepository caseRepository;
    private final PaymentRepository paymentRepository;
    private final FraudReviewSlaPolicy slaPolicy;
    private final MeterRegistry meterRegistry;
    private final Duration leaseDuration;

    public FraudReviewQueueService(
            FraudReviewCaseRepository caseRepository,
            PaymentRepository paymentRepository,
            FraudReviewSlaPolicy slaPolicy,
            MeterRegistry meterRegistry,
            @Value("${nexapay.payment.fraud-review.lease-duration:15m}")
            Duration leaseDuration) {
        this.caseRepository = caseRepository;
        this.paymentRepository = paymentRepository;
        this.slaPolicy = slaPolicy;
        this.meterRegistry = meterRegistry;
        this.leaseDuration = leaseDuration;
    }

    @Transactional
    public void openCase(UUID paymentId, OffsetDateTime openedAt) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot open fraud review case for missing payment: "
                                + paymentId
                ));

        FraudReviewPriority priority = slaPolicy.priority(
                payment.getFraudRiskScore(),
                payment.getAmount()
        );

        OffsetDateTime slaDueAt = openedAt.plus(
                slaPolicy.slaFor(priority)
        );

        int inserted = caseRepository.insertOpenCaseIfAbsent(
                paymentId,
                openedAt,
                priority.name(),
                slaDueAt
        );

        if (inserted == 1) {
            meterRegistry.counter(
                    "nexapay.payment.fraud_review_queue.opened",
                    "priority",
                    priority.name()
            ).increment();
        }
    }

    @Transactional(readOnly = true)
    public List<FraudReviewCaseResponse> listOpenCases(
            FraudReviewPriority priority,
            Boolean overdue,
            Boolean available) {

        OffsetDateTime now = OffsetDateTime.now();
        List<FraudReviewCase> cases =
                caseRepository.findOpenCasesOrdered();

        Map<UUID, Payment> payments = new LinkedHashMap<>();
        paymentRepository.findAllById(
                cases.stream()
                        .map(FraudReviewCase::getPaymentId)
                        .toList()
        ).forEach(payment -> payments.put(payment.getId(), payment));

        return cases.stream()
                .map(reviewCase -> toResponse(
                        reviewCase,
                        payments.get(reviewCase.getPaymentId()),
                        now
                ))
                .filter(response ->
                        priority == null
                                || response.priority() == priority
                )
                .filter(response ->
                        overdue == null
                                || response.overdue() == overdue
                )
                .filter(response ->
                        available == null
                                || response.available() == available
                )
                .toList();
    }

    @Transactional
    public FraudReviewCaseResponse claim(
            UUID paymentId,
            String reviewer) {

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plus(leaseDuration);

        int updated = caseRepository.claim(
                paymentId,
                reviewer,
                now,
                expiresAt
        );

        if (updated == 0) {
            meterRegistry.counter(
                    "nexapay.payment.fraud_review_queue.claim_conflict"
            ).increment();

            if (!caseRepository.existsById(paymentId)) {
                throw new FraudReviewCaseNotFoundException(paymentId);
            }

            throw new FraudReviewCaseConflictException(
                    paymentId,
                    "Caso já possui outro owner com lease ativo"
            );
        }

        meterRegistry.counter(
                "nexapay.payment.fraud_review_queue.claimed"
        ).increment();

        return responseFor(paymentId, OffsetDateTime.now());
    }

    @Transactional
    public FraudReviewCaseResponse release(
            UUID paymentId,
            String reviewer) {

        int updated = caseRepository.release(paymentId, reviewer);

        if (updated == 0) {
            if (!caseRepository.existsById(paymentId)) {
                throw new FraudReviewCaseNotFoundException(paymentId);
            }

            throw new FraudReviewCaseConflictException(
                    paymentId,
                    "Somente o owner atual pode liberar o caso"
            );
        }

        meterRegistry.counter(
                "nexapay.payment.fraud_review_queue.released"
        ).increment();

        return responseFor(paymentId, OffsetDateTime.now());
    }

    @Transactional
    public void resolveOwnedCase(
            UUID paymentId,
            String reviewer,
            String resolution,
            OffsetDateTime resolvedAt) {

        int updated = caseRepository.resolveWithActiveLease(
                paymentId,
                reviewer,
                resolution,
                resolvedAt
        );

        if (updated == 0) {
            if (!caseRepository.existsById(paymentId)) {
                throw new FraudReviewCaseNotFoundException(paymentId);
            }

            throw new FraudReviewCaseConflictException(
                    paymentId,
                    "Revisão exige ownership com lease ativo"
            );
        }

        meterRegistry.counter(
                "nexapay.payment.fraud_review_queue.resolved",
                "resolution",
                resolution
        ).increment();
    }

    private FraudReviewCaseResponse responseFor(
            UUID paymentId,
            OffsetDateTime now) {

        FraudReviewCase reviewCase = caseRepository.findById(paymentId)
                .orElseThrow(() ->
                        new FraudReviewCaseNotFoundException(paymentId)
                );

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Fraud review case references missing payment: "
                                        + paymentId
                        )
                );

        return toResponse(reviewCase, payment, now);
    }

    private FraudReviewCaseResponse toResponse(
            FraudReviewCase reviewCase,
            Payment payment,
            OffsetDateTime now) {

        if (payment == null) {
            throw new IllegalStateException(
                    "Fraud review case references missing payment: "
                            + reviewCase.getPaymentId()
            );
        }

        boolean isAvailable =
                reviewCase.getClaimedBy() == null
                        || reviewCase.getClaimExpiresAt() == null
                        || !reviewCase.getClaimExpiresAt().isAfter(now);

        boolean isOverdue =
                !reviewCase.getSlaDueAt().isAfter(now);

        long ageSeconds = Math.max(
                0,
                Duration.between(
                        reviewCase.getOpenedAt(),
                        now
                ).toSeconds()
        );

        long remainingSlaSeconds = Duration.between(
                now,
                reviewCase.getSlaDueAt()
        ).toSeconds();

        return new FraudReviewCaseResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getPixKey(),
                payment.getFraudRiskScore(),
                payment.getFraudReason(),
                reviewCase.getOpenedAt(),
                reviewCase.getClaimedBy(),
                reviewCase.getClaimedAt(),
                reviewCase.getClaimExpiresAt(),
                isAvailable,
                reviewCase.getPriority(),
                reviewCase.getSlaDueAt(),
                isOverdue,
                reviewCase.getEscalatedAt(),
                ageSeconds,
                remainingSlaSeconds
        );
    }
}
