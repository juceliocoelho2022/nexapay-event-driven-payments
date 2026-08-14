package br.com.nexapay.ledger;

import br.com.nexapay.ledger.event.AccountCreditedEvent;
import br.com.nexapay.ledger.repository.LedgerEntryRepository;
import br.com.nexapay.ledger.service.LedgerService;
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
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false"
        }
)
class LedgerIdempotencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("nexapay_ledger_test")
                    .withUsername("nexapay")
                    .withPassword("nexapay");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerEntryRepository repository;

    @Test
    void shouldIgnoreDuplicatedEvent() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        AccountCreditedEvent event = new AccountCreditedEvent(
                eventId,
                accountId,
                "ACC-TEST-01",
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        ledgerService.recordCredit(event);
        ledgerService.recordCredit(event);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.existsByEventId(eventId)).isTrue();
    }
}