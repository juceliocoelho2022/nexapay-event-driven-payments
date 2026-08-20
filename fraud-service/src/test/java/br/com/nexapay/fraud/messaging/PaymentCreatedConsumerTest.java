package br.com.nexapay.fraud.messaging;

import br.com.nexapay.fraud.service.FraudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentCreatedConsumerTest {

    @Mock
    private FraudService fraudService;

    private PaymentCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new PaymentCreatedConsumer(objectMapper, fraudService);
        MDC.clear();
    }

    @Test
    void shouldRejectMalformedPayloadBeforeCallingFraudService() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "nexapay.payment.created.v1",
                0,
                0L,
                "payment-key",
                "{invalid-json"
        );

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(InvalidFraudEventPayloadException.class)
                .hasMessageContaining("nexapay.payment.created.v1");

        verifyNoInteractions(fraudService);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldExposeCorrelationIdInMdcWhileProcessingAndClearItAfterwards() {
        String correlationId = "corr-payment-fraud-123";
        String payload = """
                {
                  "eventId":"11111111-1111-1111-1111-111111111111",
                  "paymentId":"22222222-2222-2222-2222-222222222222",
                  "payerAccountId":"ACC-123",
                  "pixKey":"pix@example.com",
                  "amount":150.00,
                  "occurredAt":"2026-08-18T10:00:00-03:00"
                }
                """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "nexapay.payment.created.v1",
                0,
                1L,
                "payment-key",
                payload
        );
        record.headers().add(new RecordHeader(
                "X-Correlation-Id",
                correlationId.getBytes(StandardCharsets.UTF_8)
        ));

        doAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
            return null;
        }).when(fraudService).analyze(any());

        consumer.consume(record);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
