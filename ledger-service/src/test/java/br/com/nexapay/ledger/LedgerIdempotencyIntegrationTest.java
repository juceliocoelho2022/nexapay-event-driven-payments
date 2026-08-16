package br.com.nexapay.ledger;

import br.com.nexapay.ledger.event.AccountCreditedEvent;
import br.com.nexapay.ledger.repository.LedgerEntryRepository;
import br.com.nexapay.ledger.service.LedgerService;
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
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldIgnoreDuplicatedEvent() {
        UUID eventId = UUID.randomUUID();
        AccountCreditedEvent event = event(eventId, "ACC-TEST-01");

        ledgerService.recordCredit(event);
        ledgerService.recordCredit(event);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.existsByEventId(eventId)).isTrue();
    }

    @Test
    void shouldIgnoreConcurrentDuplicatedEventWithoutThrowing() throws Exception {
        UUID eventId = UUID.randomUUID();
        AccountCreditedEvent event = event(eventId, "ACC-CONCURRENT-LEDGER");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> invokeWhenReleased(event, ready, start));
            Future<?> second = executor.submit(() -> invokeWhenReleased(event, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.existsByEventId(eventId)).isTrue();
    }

    private void invokeWhenReleased(
            AccountCreditedEvent event,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent ledger test start latch timed out");
            }
            ledgerService.recordCredit(event);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent ledger test interrupted", exception);
        }
    }

    private AccountCreditedEvent event(UUID eventId, String accountNumber) {
        return new AccountCreditedEvent(
                eventId,
                UUID.randomUUID(),
                accountNumber,
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
