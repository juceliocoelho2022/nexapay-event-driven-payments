package br.com.nexapay.payment.messaging;

public final class KafkaTopics {

    public static final String PAYMENT_CREATED = "nexapay.payment.created.v1";
    public static final String FRAUD_DECISION_MADE = "nexapay.fraud.decision-made.v1";

    private KafkaTopics() {
    }
}
