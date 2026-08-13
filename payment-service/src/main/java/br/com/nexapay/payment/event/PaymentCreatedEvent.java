package br.com.nexapay.payment.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentCreatedEvent(
        UUID eventId,
        UUID paymentId,
        String payerAccountId,
        String pixKey,
        BigDecimal amount,
        OffsetDateTime occurredAt
) {
}
