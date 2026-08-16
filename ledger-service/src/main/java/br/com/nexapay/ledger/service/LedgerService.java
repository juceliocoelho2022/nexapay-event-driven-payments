package br.com.nexapay.ledger.service;

import br.com.nexapay.ledger.api.LedgerEntryResponse;
import br.com.nexapay.ledger.domain.LedgerEntry;
import br.com.nexapay.ledger.domain.LedgerEntryType;
import br.com.nexapay.ledger.event.AccountCreditedEvent;
import br.com.nexapay.ledger.event.AccountDebitedEvent;
import br.com.nexapay.ledger.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository repository;

    public LedgerService(LedgerEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordCredit(AccountCreditedEvent event) {
        record(
                event.eventId(),
                event.accountId(),
                event.accountNumber(),
                LedgerEntryType.CREDIT,
                event.amount(),
                event.balanceAfter(),
                event.occurredAt()
        );
    }

    @Transactional
    public void recordDebit(AccountDebitedEvent event) {
        record(
                event.eventId(),
                event.accountId(),
                event.accountNumber(),
                LedgerEntryType.DEBIT,
                event.amount(),
                event.balanceAfter(),
                event.occurredAt()
        );
    }

    private void record(
            UUID eventId,
            UUID accountId,
            String accountNumber,
            LedgerEntryType entryType,
            java.math.BigDecimal amount,
            java.math.BigDecimal balanceAfter,
            java.time.OffsetDateTime occurredAt
    ) {
        LedgerEntry entry = LedgerEntry.create(
                eventId,
                accountId,
                accountNumber,
                entryType,
                amount,
                balanceAfter,
                occurredAt
        );

        int inserted = repository.insertIfEventAbsent(
                entry.getId(),
                entry.getEventId(),
                entry.getAccountId(),
                entry.getAccountNumber(),
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getOccurredAt(),
                entry.getRecordedAt()
        );

        if (inserted == 0) {
            log.info("Ignoring duplicated ledger event. eventId={}", eventId);
            return;
        }

        log.info(
                "Ledger {} recorded. eventId={}, accountId={}, amount={}",
                entryType.name().toLowerCase(),
                eventId,
                accountId,
                amount
        );
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getEntriesByAccountId(
            UUID accountId,
            Pageable pageable
    ) {
        return repository
                .findByAccountIdOrderByOccurredAtDesc(accountId, pageable)
                .map(LedgerEntryResponse::from);
    }
}
