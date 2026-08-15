package br.com.nexapay.ledger.repository;

import br.com.nexapay.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByEventId(UUID eventId);

    Page<LedgerEntry> findByAccountIdOrderByOccurredAtDesc(
            UUID accountId,
            Pageable pageable
    );
}