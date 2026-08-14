package br.com.nexapay.account.exception;

public class DuplicateAccountNumberException extends RuntimeException {
    public DuplicateAccountNumberException(String accountNumber) {
        super("Account number already exists: " + accountNumber);
    }
}
