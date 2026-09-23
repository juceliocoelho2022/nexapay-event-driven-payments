package br.com.nexapay.payment.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FraudDecisionMadeEvent(
        UUID eventId,
        UUID fraudDecisionId,
        UUID paymentId,
        String decision,
        int riskScore,
        String reason,
        OffsetDateTime occurredAt
) {
}
