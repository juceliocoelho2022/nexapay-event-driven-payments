package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.ManualFraudReviewDecision;
import br.com.nexapay.payment.domain.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ManualFraudReviewResponse(
        UUID paymentId,
        ManualFraudReviewDecision decision,
        PaymentStatus previousStatus,
        PaymentStatus finalStatus,
        String reviewerSubject,
        OffsetDateTime reviewedAt
) {
}
