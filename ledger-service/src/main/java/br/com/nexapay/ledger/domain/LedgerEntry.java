package br.com.nexapay.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "ledger_entries",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ledger_entries_event_id", columnNames = "event_id")
        }
)
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private LedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(
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
        this.id = id;
        this.eventId = eventId;
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
    }

    public static LedgerEntry create(
            UUID eventId,
            UUID accountId,
            String accountNumber,
            LedgerEntryType entryType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            OffsetDateTime occurredAt
    ) {
        return new LedgerEntry(
                UUID.randomUUID(),
                eventId,
                accountId,
                accountNumber,
                entryType,
                amount,
                balanceAfter,
                occurredAt,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }
}