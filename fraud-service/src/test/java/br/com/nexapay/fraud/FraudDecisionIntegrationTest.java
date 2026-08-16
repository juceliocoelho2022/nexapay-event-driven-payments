package br.com.nexapay.fraud;

import br.com.nexapay.fraud.domain.FraudDecision;
import br.com.nexapay.fraud.domain.FraudDecisionType;
import br.com.nexapay.fraud.event.PaymentCreatedEvent;
import br.com.nexapay.fraud.repository.FraudDecisionRepository;
import br.com.nexapay.fraud.service.FraudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class FraudDecisionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("nexapay_fraud_test")
                    .withUsername("nexapay")
                    .withPassword("nexapay");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private FraudService fraudService;

    @Autowired
    private FraudDecisionRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldApproveNormalPayment() {
        FraudDecision decision = fraudService.analyze(event("1000.00"));

        assertThat(decision.getDecision()).isEqualTo(FraudDecisionType.APPROVED);
        assertThat(decision.getRiskScore()).isEqualTo(20);
    }

    @Test
    void shouldSendElevatedPaymentToReview() {
        FraudDecision decision = fraudService.analyze(event("5000.00"));

        assertThat(decision.getDecision()).isEqualTo(FraudDecisionType.REVIEW);
        assertThat(decision.getRiskScore()).isEqualTo(70);
    }

    @Test
    void shouldBlockHighRiskPayment() {
        FraudDecision decision = fraudService.analyze(event("10000.00"));

        assertThat(decision.getDecision()).isEqualTo(FraudDecisionType.BLOCKED);
        assertThat(decision.getRiskScore()).isEqualTo(95);
    }

    @Test
    void shouldIgnoreDuplicatedPaymentEvent() {
        PaymentCreatedEvent event = event("1000.00");

        FraudDecision first = fraudService.analyze(event);
        FraudDecision second = fraudService.analyze(event);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    private PaymentCreatedEvent event(String amount) {
        return new PaymentCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ACC-TEST-01",
                "customer@example.com",
                new BigDecimal(amount),
                OffsetDateTime.parse("2026-08-15T18:00:00Z")
        );
    }
}
