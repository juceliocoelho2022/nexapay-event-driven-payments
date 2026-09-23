package br.com.nexapay.payment.messaging;

import br.com.nexapay.payment.event.FraudDecisionMadeEvent;
import br.com.nexapay.payment.service.FraudDecisionStateMachineService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class FraudDecisionConsumer {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_MDC = "correlationId";

    private final ObjectMapper objectMapper;
    private final FraudDecisionStateMachineService stateMachineService;

    public FraudDecisionConsumer(
            ObjectMapper objectMapper,
            FraudDecisionStateMachineService stateMachineService) {
        this.objectMapper = objectMapper;
        this.stateMachineService = stateMachineService;
    }

    @KafkaListener(
            topics = KafkaTopics.FRAUD_DECISION_MADE,
            groupId = "nexapay-payment-service"
    )
    public void consume(ConsumerRecord<String, String> record) {
        String correlationId = extractCorrelationId(record);

        if (correlationId != null) {
            MDC.put(CORRELATION_MDC, correlationId);
        }

        try {
            FraudDecisionMadeEvent event;

            try {
                event = objectMapper.readValue(
                        record.value(),
                        FraudDecisionMadeEvent.class
                );
            } catch (JsonProcessingException exception) {
                throw new InvalidFraudDecisionEventPayloadException(
                        KafkaTopics.FRAUD_DECISION_MADE,
                        exception
                );
            }

            stateMachineService.apply(event);
        } finally {
            MDC.remove(CORRELATION_MDC);
        }
    }

    private String extractCorrelationId(
            ConsumerRecord<String, String> record) {

        Header header = record.headers().lastHeader(CORRELATION_HEADER);

        if (header == null
                || header.value() == null
                || header.value().length == 0) {
            return null;
        }

        return new String(
                header.value(),
                StandardCharsets.UTF_8
        );
    }
}
