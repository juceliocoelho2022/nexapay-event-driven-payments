package br.com.nexapay.payment.messaging;

public class InvalidFraudDecisionEventPayloadException extends RuntimeException {

    public InvalidFraudDecisionEventPayloadException(
            String topic,
            Throwable cause) {
        super("Invalid event payload received from topic " + topic, cause);
    }
}
