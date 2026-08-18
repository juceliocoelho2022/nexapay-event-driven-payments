package br.com.nexapay.fraud.messaging;

import br.com.nexapay.fraud.event.PaymentCreatedEvent;
import br.com.nexapay.fraud.service.FraudService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PaymentCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreatedConsumer.class);
    private static final String PAYMENT_CREATED_TOPIC = "nexapay.payment.created.v1";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final ObjectMapper objectMapper;
    private final FraudService fraudService;

    public PaymentCreatedConsumer(
            ObjectMapper objectMapper,
            FraudService fraudService
    ) {
        this.objectMapper = objectMapper;
        this.fraudService = fraudService;
    }

    @KafkaListener(
            topics = PAYMENT_CREATED_TOPIC,
            groupId = "nexapay-fraud-service"
    )
    public void consume(ConsumerRecord<String, String> record) {
        String correlationId = extractCorrelationId(record);
        if (correlationId != null) {
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        }

        try {
            PaymentCreatedEvent event;
            try {
                event = objectMapper.readValue(record.value(), PaymentCreatedEvent.class);
            } catch (JsonProcessingException exception) {
                throw new InvalidFraudEventPayloadException(PAYMENT_CREATED_TOPIC, exception);
            }

            log.info(
                    "Received payment event for fraud analysis. eventId={}, paymentId={}",
                    event.eventId(),
                    event.paymentId()
            );

            fraudService.analyze(event);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private String extractCorrelationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(CORRELATION_ID_HEADER);
        if (header == null || header.value() == null || header.value().length == 0) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
