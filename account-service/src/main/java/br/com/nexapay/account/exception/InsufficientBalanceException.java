package br.com.nexapay.account.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(UUID accountId, BigDecimal balance, BigDecimal requestedAmount) {
        super("Insufficient balance for account " + accountId
                + ". Available: " + balance + ", requested: " + requestedAmount);
    }
}
