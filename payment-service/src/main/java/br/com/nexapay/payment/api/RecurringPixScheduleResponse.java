package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.RecurrenceFrequency;
import br.com.nexapay.payment.domain.RecurringScheduleStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringPixScheduleResponse(
        UUID id,
        String payerAccountId,
        String pixKey,
        BigDecimal amount,
        String description,
        RecurrenceFrequency frequency,
        RecurringScheduleStatus status,
        OffsetDateTime nextOccurrenceAt,
        int remainingOccurrences,
        OffsetDateTime createdAt
) {
}
