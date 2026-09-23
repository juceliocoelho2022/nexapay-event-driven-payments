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
        OffsetDateTime createdAt,
        OffsetDateTime scheduledAt,
        OffsetDateTime executedAt,
        String fraudDecision,
        Integer fraudRiskScore,
        String fraudReason,
        OffsetDateTime fraudDecidedAt
) {

    public PaymentResponse(
            UUID id,
            String payerAccountId,
            String pixKey,
            BigDecimal amount,
            String description,
            PaymentStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime scheduledAt,
            OffsetDateTime executedAt) {
        this(
                id,
                payerAccountId,
                pixKey,
                amount,
                description,
                status,
                createdAt,
                scheduledAt,
                executedAt,
                null,
                null,
                null,
                null
        );
    }
}
