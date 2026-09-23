package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.CancellationTargetType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CancellationResponse(
        CancellationTargetType targetType,
        UUID targetId,
        OffsetDateTime cancelledAt,
        int affectedScheduledPayments
) {
}
