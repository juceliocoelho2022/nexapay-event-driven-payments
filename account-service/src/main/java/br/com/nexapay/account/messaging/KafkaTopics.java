package br.com.nexapay.account.messaging;

public final class KafkaTopics {

    public static final String ACCOUNT_CREDITED = "nexapay.account.credited.v1";
    public static final String ACCOUNT_DEBITED = "nexapay.account.debited.v1";

    private KafkaTopics() {
    }
}
