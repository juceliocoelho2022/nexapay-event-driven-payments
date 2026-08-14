package br.com.nexapay.account.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(UUID id) {
        super("Account not found: " + id);
    }
}
