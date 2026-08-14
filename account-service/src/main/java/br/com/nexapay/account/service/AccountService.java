package br.com.nexapay.account.service;

import br.com.nexapay.account.api.AccountResponse;
import br.com.nexapay.account.api.AmountRequest;
import br.com.nexapay.account.api.CreateAccountRequest;
import br.com.nexapay.account.domain.Account;
import br.com.nexapay.account.domain.OutboxEvent;
import br.com.nexapay.account.event.AccountCreditedEvent;
import br.com.nexapay.account.event.AccountDebitedEvent;
import br.com.nexapay.account.exception.AccountNotFoundException;
import br.com.nexapay.account.exception.DuplicateAccountNumberException;
import br.com.nexapay.account.repository.AccountRepository;
import br.com.nexapay.account.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AccountService {

    private static final String ACCOUNT_CREDITED = "ACCOUNT_CREDITED";
    private static final String ACCOUNT_DEBITED = "ACCOUNT_DEBITED";

    private final AccountRepository accountRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public AccountService(AccountRepository accountRepository,
                          OutboxEventRepository outboxEventRepository,
                          ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        String accountNumber = request.accountNumber().trim();

        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw new DuplicateAccountNumberException(accountNumber);
        }

        Account account = Account.create(accountNumber, request.holderName());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(UUID id) {
        return AccountResponse.from(findAccount(id));
    }

    @Transactional
    public AccountResponse credit(UUID id, AmountRequest request) {
        Account account = findAccountForUpdate(id);
        BigDecimal amount = request.amount();

        account.credit(amount);
        recordCreditedEvent(account, amount);

        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse debit(UUID id, AmountRequest request) {
        Account account = findAccountForUpdate(id);
        BigDecimal amount = request.amount();

        account.debit(amount);
        recordDebitedEvent(account, amount);

        return AccountResponse.from(account);
    }

    private void recordCreditedEvent(Account account, BigDecimal amount) {
        UUID eventId = UUID.randomUUID();
        AccountCreditedEvent event = new AccountCreditedEvent(
                eventId,
                account.getId(),
                account.getAccountNumber(),
                amount,
                account.getBalance(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        saveOutbox(eventId, account.getId(), ACCOUNT_CREDITED, event);
    }

    private void recordDebitedEvent(Account account, BigDecimal amount) {
        UUID eventId = UUID.randomUUID();
        AccountDebitedEvent event = new AccountDebitedEvent(
                eventId,
                account.getId(),
                account.getAccountNumber(),
                amount,
                account.getBalance(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        saveOutbox(eventId, account.getId(), ACCOUNT_DEBITED, event);
    }

    private void saveOutbox(UUID eventId, UUID aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(OutboxEvent.pending(eventId, aggregateId, eventType, payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize account event " + eventType, exception);
        }
    }

    private Account findAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    private Account findAccountForUpdate(UUID id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
