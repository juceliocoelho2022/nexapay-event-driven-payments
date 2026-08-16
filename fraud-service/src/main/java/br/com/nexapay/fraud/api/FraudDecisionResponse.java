package br.com.nexapay.fraud.api;

import br.com.nexapay.fraud.domain.FraudDecision;
import br.com.nexapay.fraud.domain.FraudDecisionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FraudDecisionResponse(
        UUID id,
        UUID eventId,
        UUID paymentId,
        String payerAccountId,
        String pixKey,
        BigDecimal amount,
        FraudDecisionType decision,
        int riskScore,
        String reason,
        OffsetDateTime occurredAt,
        OffsetDateTime analyzedAt
) {

    public static FraudDecisionResponse from(FraudDecision decision) {
        return new FraudDecisionResponse(
                decision.getId(),
                decision.getEventId(),
                decision.getPaymentId(),
                decision.getPayerAccountId(),
                decision.getPixKey(),
                decision.getAmount(),
                decision.getDecision(),
                decision.getRiskScore(),
                decision.getReason(),
                decision.getOccurredAt(),
                decision.getAnalyzedAt()
        );
    }
}
