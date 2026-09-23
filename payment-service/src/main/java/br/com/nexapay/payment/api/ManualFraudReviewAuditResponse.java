package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.ManualFraudReviewDecision;
import br.com.nexapay.payment.domain.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ManualFraudReviewAuditResponse(
        UUID id,
        UUID paymentId,
        ManualFraudReviewDecision decision,
        String reason,
        String reviewerSubject,
        OffsetDateTime reviewedAt,
        PaymentStatus previousStatus,
        PaymentStatus finalStatus
) {
}
