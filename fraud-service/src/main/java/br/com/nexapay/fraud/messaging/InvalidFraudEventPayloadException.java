package br.com.nexapay.fraud.messaging;

public class InvalidFraudEventPayloadException extends RuntimeException {
    public InvalidFraudEventPayloadException(String sourceTopic, Throwable cause) {
        super("Invalid event payload from " + sourceTopic, cause);
    }
}
