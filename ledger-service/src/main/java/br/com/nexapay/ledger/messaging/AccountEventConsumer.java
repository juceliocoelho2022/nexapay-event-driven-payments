package br.com.nexapay.ledger.messaging;

import br.com.nexapay.ledger.event.AccountCreditedEvent;
import br.com.nexapay.ledger.event.AccountDebitedEvent;
import br.com.nexapay.ledger.service.LedgerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AccountEventConsumer {

    private final ObjectMapper objectMapper;
    private final LedgerService ledgerService;

    public AccountEventConsumer(
            ObjectMapper objectMapper,
            LedgerService ledgerService
    ) {
        this.objectMapper = objectMapper;
        this.ledgerService = ledgerService;
    }

    @KafkaListener(
            topics = "nexapay.account.credited.v1",
            groupId = "nexapay-ledger-service"
    )
    public void consumeCredit(String payload) {
        AccountCreditedEvent event = readCredit(payload);
        ledgerService.recordCredit(event);
    }

    @KafkaListener(
            topics = "nexapay.account.debited.v1",
            groupId = "nexapay-ledger-service"
    )
    public void consumeDebit(String payload) {
        AccountDebitedEvent event = readDebit(payload);
        ledgerService.recordDebit(event);
    }

    private AccountCreditedEvent readCredit(String payload) {
        try {
            return objectMapper.readValue(payload, AccountCreditedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidLedgerEventPayloadException(
                    "Invalid payload for nexapay.account.credited.v1",
                    exception
            );
        }
    }

    private AccountDebitedEvent readDebit(String payload) {
        try {
            return objectMapper.readValue(payload, AccountDebitedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidLedgerEventPayloadException(
                    "Invalid payload for nexapay.account.debited.v1",
                    exception
            );
        }
    }
}
