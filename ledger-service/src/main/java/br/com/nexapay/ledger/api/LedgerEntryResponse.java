package br.com.nexapay.ledger.api;

import br.com.nexapay.ledger.domain.LedgerEntry;
import br.com.nexapay.ledger.domain.LedgerEntryType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID eventId,
        UUID accountId,
        String accountNumber,
        LedgerEntryType entryType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        OffsetDateTime occurredAt,
        OffsetDateTime recordedAt
) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getEventId(),
                entry.getAccountId(),
                entry.getAccountNumber(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getOccurredAt(),
                entry.getRecordedAt()
        );
    }
}