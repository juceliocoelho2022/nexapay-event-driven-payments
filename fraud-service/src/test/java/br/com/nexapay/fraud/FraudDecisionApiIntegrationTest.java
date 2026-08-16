package br.com.nexapay.fraud;

import br.com.nexapay.fraud.event.PaymentCreatedEvent;
import br.com.nexapay.fraud.repository.FraudDecisionRepository;
import br.com.nexapay.fraud.service.FraudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
class FraudDecisionApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("nexapay_fraud_api_test")
                    .withUsername("nexapay")
                    .withPassword("nexapay");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FraudService fraudService;

    @Autowired
    private FraudDecisionRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldReturnFraudDecisionByPaymentId() throws Exception {
        UUID paymentId = UUID.randomUUID();

        fraudService.analyze(new PaymentCreatedEvent(
                UUID.randomUUID(),
                paymentId,
                "ACC-FRAUD-API-01",
                "api-test@nexapay.com",
                new BigDecimal("12000.00"),
                OffsetDateTime.parse("2026-08-15T18:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/fraud/payments/{paymentId}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.amount").value(12000.00))
                .andExpect(jsonPath("$.decision").value("BLOCKED"))
                .andExpect(jsonPath("$.riskScore").value(95));
    }

    @Test
    void shouldReturnNotFoundWhenDecisionDoesNotExist() throws Exception {
        UUID paymentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/fraud/payments/{paymentId}", paymentId))
                .andExpect(status().isNotFound());
    }
}
