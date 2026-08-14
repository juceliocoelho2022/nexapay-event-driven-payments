package br.com.nexapay.ledger.service;

import br.com.nexapay.ledger.domain.LedgerEntry;
import br.com.nexapay.ledger.domain.LedgerEntryType;
import br.com.nexapay.ledger.event.AccountCreditedEvent;
import br.com.nexapay.ledger.event.AccountDebitedEvent;
import br.com.nexapay.ledger.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository repository;

    public LedgerService(LedgerEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordCredit(AccountCreditedEvent event) {
        if (repository.existsByEventId(event.eventId())) {
            log.info("Ignoring duplicated ledger event. eventId={}", event.eventId());
            return;
        }

        LedgerEntry entry = LedgerEntry.create(
                event.eventId(),
                event.accountId(),
                event.accountNumber(),
                LedgerEntryType.CREDIT,
                event.amount(),
                event.balanceAfter(),
                event.occurredAt()
        );

        repository.save(entry);

        log.info(
                "Ledger credit recorded. eventId={}, accountId={}, amount={}",
                event.eventId(),
                event.accountId(),
                event.amount()
        );
    }

    @Transactional
    public void recordDebit(AccountDebitedEvent event) {
        if (repository.existsByEventId(event.eventId())) {
            log.info("Ignoring duplicated ledger event. eventId={}", event.eventId());
            return;
        }

        LedgerEntry entry = LedgerEntry.create(
                event.eventId(),
                event.accountId(),
                event.accountNumber(),
                LedgerEntryType.DEBIT,
                event.amount(),
                event.balanceAfter(),
                event.occurredAt()
        );

        repository.save(entry);

        log.info(
                "Ledger debit recorded. eventId={}, accountId={}, amount={}",
                event.eventId(),
                event.accountId(),
                event.amount()
        );
    }
}