package br.com.nexapay.account.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountDebitedEvent(
        UUID eventId,
        UUID accountId,
        String accountNumber,
        BigDecimal amount,
        BigDecimal balanceAfter,
        OffsetDateTime occurredAt
) {
}
