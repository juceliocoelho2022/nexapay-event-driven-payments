package br.com.nexapay.account.domain;

import br.com.nexapay.account.exception.InsufficientBalanceException;
import br.com.nexapay.account.exception.InvalidAccountOperationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, length = 30)
    private String accountNumber;

    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Account() {
    }

    private Account(UUID id, String accountNumber, String holderName, BigDecimal balance,
                    AccountStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.balance = balance;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Account create(String accountNumber, String holderName) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new Account(
                UUID.randomUUID(),
                accountNumber.trim(),
                holderName.trim(),
                BigDecimal.ZERO.setScale(2),
                AccountStatus.ACTIVE,
                now,
                now
        );
    }

    public void credit(BigDecimal amount) {
        ensureActive();
        ensurePositiveAmount(amount);
        this.balance = this.balance.add(amount);
        touch();
    }

    public void debit(BigDecimal amount) {
        ensureActive();
        ensurePositiveAmount(amount);

        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(this.id, this.balance, amount);
        }

        this.balance = this.balance.subtract(amount);
        touch();
    }

    private void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new InvalidAccountOperationException("Account " + id + " is not active");
        }
    }

    private void ensurePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAccountOperationException("Amount must be greater than zero");
        }
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
