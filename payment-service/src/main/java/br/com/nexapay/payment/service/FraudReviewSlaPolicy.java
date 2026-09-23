package br.com.nexapay.payment.service;

import br.com.nexapay.payment.domain.FraudReviewPriority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class FraudReviewSlaPolicy {

    private final Duration p1Sla;
    private final Duration p2Sla;
    private final Duration p3Sla;

    public FraudReviewSlaPolicy(
            @Value("${nexapay.payment.fraud-review.sla.p1:15m}") Duration p1Sla,
            @Value("${nexapay.payment.fraud-review.sla.p2:30m}") Duration p2Sla,
            @Value("${nexapay.payment.fraud-review.sla.p3:60m}") Duration p3Sla) {
        this.p1Sla = p1Sla;
        this.p2Sla = p2Sla;
        this.p3Sla = p3Sla;
    }

    public FraudReviewPriority priority(
            Integer riskScore,
            BigDecimal amount) {

        int score = riskScore == null ? 0 : riskScore;

        if (score >= 85 || amount.compareTo(new BigDecimal("9000.00")) >= 0) {
            return FraudReviewPriority.P1;
        }

        if (score >= 80 || amount.compareTo(new BigDecimal("7500.00")) >= 0) {
            return FraudReviewPriority.P2;
        }

        return FraudReviewPriority.P3;
    }

    public Duration slaFor(FraudReviewPriority priority) {
        return switch (priority) {
            case P1 -> p1Sla;
            case P2 -> p2Sla;
            case P3 -> p3Sla;
        };
    }
}
