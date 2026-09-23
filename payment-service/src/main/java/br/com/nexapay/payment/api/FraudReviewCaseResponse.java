package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.FraudReviewPriority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FraudReviewCaseResponse(
        UUID paymentId,
        BigDecimal amount,
        String pixKey,
        Integer riskScore,
        String fraudReason,
        OffsetDateTime openedAt,
        String claimedBy,
        OffsetDateTime claimedAt,
        OffsetDateTime claimExpiresAt,
        boolean available,
        FraudReviewPriority priority,
        OffsetDateTime slaDueAt,
        boolean overdue,
        OffsetDateTime escalatedAt,
        long ageSeconds,
        long remainingSlaSeconds
) {
}
