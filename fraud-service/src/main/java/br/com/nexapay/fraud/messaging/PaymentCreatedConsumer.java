package br.com.nexapay.fraud.messaging;

import br.com.nexapay.fraud.event.PaymentCreatedEvent;
import br.com.nexapay.fraud.service.FraudService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCreatedConsumer.class);
    private static final String PAYMENT_CREATED_TOPIC = "nexapay.payment.created.v1";

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
    public void consume(String payload) throws JsonProcessingException {
        PaymentCreatedEvent event = objectMapper.readValue(payload, PaymentCreatedEvent.class);

        log.info(
                "Received payment event for fraud analysis. eventId={}, paymentId={}",
                event.eventId(),
                event.paymentId()
        );

        fraudService.analyze(event);
    }
}
