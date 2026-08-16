package br.com.nexapay.fraud.service;

import br.com.nexapay.fraud.domain.FraudDecisionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FraudRuleEngine {

    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal BLOCK_THRESHOLD = new BigDecimal("10000.00");

    public RiskAssessment assess(BigDecimal amount) {
        if (amount.compareTo(BLOCK_THRESHOLD) >= 0) {
            return new RiskAssessment(
                    FraudDecisionType.BLOCKED,
                    95,
                    "Payment amount reached the high-risk threshold"
            );
        }

        if (amount.compareTo(REVIEW_THRESHOLD) >= 0) {
            return new RiskAssessment(
                    FraudDecisionType.REVIEW,
                    70,
                    "Payment amount requires manual risk review"
            );
        }

        return new RiskAssessment(
                FraudDecisionType.APPROVED,
                20,
                "Payment amount is within the normal risk range"
        );
    }

    public record RiskAssessment(
            FraudDecisionType decision,
            int riskScore,
            String reason
    ) {
    }
}
