package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.ManualFraudReviewDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualFraudReviewRequest(
        @NotNull
        ManualFraudReviewDecision decision,

        @NotBlank
        @Size(max = 500)
        String reason
) {
}
