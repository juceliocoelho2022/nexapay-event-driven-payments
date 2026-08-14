package br.com.nexapay.account.service;

import br.com.nexapay.account.api.AccountResponse;
import br.com.nexapay.account.api.AmountRequest;
import br.com.nexapay.account.api.CreateAccountRequest;
import br.com.nexapay.account.domain.Account;
import br.com.nexapay.account.exception.AccountNotFoundException;
import br.com.nexapay.account.exception.DuplicateAccountNumberException;
import br.com.nexapay.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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
        Account account = findAccount(id);
        account.credit(request.amount());
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse debit(UUID id, AmountRequest request) {
        Account account = findAccount(id);
        account.debit(request.amount());
        return AccountResponse.from(account);
    }

    private Account findAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
