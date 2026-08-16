package br.com.nexapay.ledger.messaging;

public class InvalidLedgerEventPayloadException extends RuntimeException {

    public InvalidLedgerEventPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
