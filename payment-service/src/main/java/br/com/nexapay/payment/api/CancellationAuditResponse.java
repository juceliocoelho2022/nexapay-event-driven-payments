package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.CancellationTargetType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CancellationAuditResponse(
        UUID id,
        CancellationTargetType targetType,
        UUID targetId,
        String reason,
        String actorSubject,
        OffsetDateTime cancelledAt,
        int affectedScheduledPayments
) {
}
