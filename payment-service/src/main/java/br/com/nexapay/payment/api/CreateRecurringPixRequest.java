package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.RecurrenceFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateRecurringPixRequest(
        @NotBlank
        @Size(max = 80)
        String payerAccountId,

        @NotBlank
        @Size(max = 180)
        String pixKey,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @Size(max = 255)
        String description,

        @NotNull
        RecurrenceFrequency frequency,

        @NotNull
        @Future
        OffsetDateTime firstOccurrenceAt,

        @Min(1)
        @Max(365)
        int occurrences
) {
}
