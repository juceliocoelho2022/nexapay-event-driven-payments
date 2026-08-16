package br.com.nexapay.ledger.messaging;

import br.com.nexapay.ledger.service.LedgerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountEventConsumerTest {

    @Mock
    private LedgerService ledgerService;

    private AccountEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountEventConsumer(new ObjectMapper(), ledgerService);
    }

    @Test
    void shouldRejectMalformedCreditPayloadBeforeCallingService() {
        assertThatThrownBy(() -> consumer.consumeCredit("{invalid-json"))
                .isInstanceOf(InvalidLedgerEventPayloadException.class)
                .hasMessageContaining("nexapay.account.credited.v1");

        verifyNoInteractions(ledgerService);
    }

    @Test
    void shouldRejectMalformedDebitPayloadBeforeCallingService() {
        assertThatThrownBy(() -> consumer.consumeDebit("not-json"))
                .isInstanceOf(InvalidLedgerEventPayloadException.class)
                .hasMessageContaining("nexapay.account.debited.v1");

        verifyNoInteractions(ledgerService);
    }
}
