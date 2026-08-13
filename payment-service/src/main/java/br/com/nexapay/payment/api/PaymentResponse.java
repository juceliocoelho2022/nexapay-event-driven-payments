package br.com.nexapay.payment.api;

import br.com.nexapay.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String payerAccountId,
        String pixKey,
        BigDecimal amount,
        String description,
        PaymentStatus status,
        OffsetDateTime createdAt
) {
}
