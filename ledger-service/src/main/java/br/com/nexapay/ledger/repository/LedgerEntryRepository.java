package br.com.nexapay.ledger.repository;

import br.com.nexapay.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByEventId(UUID eventId);

    @Modifying
    @Query(value = """
            INSERT INTO ledger_entries (
                id, event_id, account_id, account_number, entry_type,
                amount, balance_after, occurred_at, recorded_at
            ) VALUES (
                :id, :eventId, :accountId, :accountNumber, :entryType,
                :amount, :balanceAfter, :occurredAt, :recordedAt
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfEventAbsent(
            @Param("id") UUID id,
            @Param("eventId") UUID eventId,
            @Param("accountId") UUID accountId,
            @Param("accountNumber") String accountNumber,
            @Param("entryType") String entryType,
            @Param("amount") BigDecimal amount,
            @Param("balanceAfter") BigDecimal balanceAfter,
            @Param("occurredAt") OffsetDateTime occurredAt,
            @Param("recordedAt") OffsetDateTime recordedAt
    );

    Page<LedgerEntry> findByAccountIdOrderByOccurredAtDesc(UUID accountId, Pageable pageable);
}
