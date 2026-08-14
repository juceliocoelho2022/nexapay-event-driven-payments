package br.com.nexapay.account.api;

import br.com.nexapay.account.domain.Account;
import br.com.nexapay.account.domain.AccountStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        String holderName,
        BigDecimal balance,
        AccountStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getHolderName(),
                account.getBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
