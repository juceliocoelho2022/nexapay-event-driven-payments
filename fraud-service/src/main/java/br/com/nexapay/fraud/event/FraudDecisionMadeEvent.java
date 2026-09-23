package br.com.nexapay.fraud.event;

import br.com.nexapay.fraud.domain.FraudDecisionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FraudDecisionMadeEvent(
        UUID eventId,
        UUID fraudDecisionId,
        UUID paymentId,
        FraudDecisionType decision,
        int riskScore,
        String reason,
        OffsetDateTime occurredAt
) {
}
