package br.com.nexapay.payment.service;

import br.com.nexapay.payment.domain.FraudReviewPriority;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FraudReviewSlaPolicyTest {

    private final FraudReviewSlaPolicy policy = new FraudReviewSlaPolicy(
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofMinutes(60)
    );

    @Test
    void shouldClassifyP1ByRiskOrAmount() {
        assertThat(policy.priority(85, new BigDecimal("5000.00")))
                .isEqualTo(FraudReviewPriority.P1);

        assertThat(policy.priority(70, new BigDecimal("9000.00")))
                .isEqualTo(FraudReviewPriority.P1);
    }

    @Test
    void shouldClassifyP2ByRiskOrAmount() {
        assertThat(policy.priority(80, new BigDecimal("5000.00")))
                .isEqualTo(FraudReviewPriority.P2);

        assertThat(policy.priority(70, new BigDecimal("7500.00")))
                .isEqualTo(FraudReviewPriority.P2);
    }

    @Test
    void shouldClassifyRemainingReviewCasesAsP3() {
        assertThat(policy.priority(70, new BigDecimal("6000.00")))
                .isEqualTo(FraudReviewPriority.P3);
    }

    @Test
    void shouldReturnConfiguredSlaDurations() {
        assertThat(policy.slaFor(FraudReviewPriority.P1))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.slaFor(FraudReviewPriority.P2))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.slaFor(FraudReviewPriority.P3))
                .isEqualTo(Duration.ofMinutes(60));
    }
}
