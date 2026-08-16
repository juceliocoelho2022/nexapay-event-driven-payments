package br.com.nexapay.fraud.messaging;

import br.com.nexapay.fraud.service.FraudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentCreatedConsumerTest {

    @Mock
    private FraudService fraudService;

    private PaymentCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentCreatedConsumer(new ObjectMapper(), fraudService);
    }

    @Test
    void shouldRejectMalformedPayloadBeforeCallingFraudService() {
        assertThatThrownBy(() -> consumer.consume("{invalid-json"))
                .isInstanceOf(InvalidFraudEventPayloadException.class)
                .hasMessageContaining("nexapay.payment.created.v1");

        verifyNoInteractions(fraudService);
    }
}
