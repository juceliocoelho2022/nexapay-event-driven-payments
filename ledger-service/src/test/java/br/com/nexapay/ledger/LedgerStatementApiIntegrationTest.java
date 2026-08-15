package br.com.nexapay.ledger;

import br.com.nexapay.ledger.event.AccountCreditedEvent;
import br.com.nexapay.ledger.event.AccountDebitedEvent;
import br.com.nexapay.ledger.repository.LedgerEntryRepository;
import br.com.nexapay.ledger.service.LedgerService;
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
@SpringBootTest(
        properties = {
                "spring.kafka.listener.auto-startup=false"
        }
)
@AutoConfigureMockMvc
class LedgerStatementApiIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerEntryRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void shouldReturnAccountStatementOrderedByMostRecentEvent() throws Exception {
        UUID accountId = UUID.randomUUID();

        ledgerService.recordCredit(
                new AccountCreditedEvent(
                        UUID.randomUUID(),
                        accountId,
                        "ACC-TEST-01",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00"),
                        OffsetDateTime.parse("2026-08-14T10:00:00Z")
                )
        );

        ledgerService.recordDebit(
                new AccountDebitedEvent(
                        UUID.randomUUID(),
                        accountId,
                        "ACC-TEST-01",
                        new BigDecimal("250.00"),
                        new BigDecimal("750.00"),
                        OffsetDateTime.parse("2026-08-14T11:00:00Z")
                )
        );

        mockMvc.perform(
                        get("/api/v1/ledger/accounts/{accountId}", accountId)
                                .param("page", "0")
                                .param("size", "20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.content[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(750.00))
                .andExpect(jsonPath("$.content[1].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(1000.00));
    }

    @Test
    void shouldPaginateAccountStatement() throws Exception {
        UUID accountId = UUID.randomUUID();

        ledgerService.recordCredit(
                new AccountCreditedEvent(
                        UUID.randomUUID(),
                        accountId,
                        "ACC-TEST-02",
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00"),
                        OffsetDateTime.parse("2026-08-14T10:00:00Z")
                )
        );

        ledgerService.recordDebit(
                new AccountDebitedEvent(
                        UUID.randomUUID(),
                        accountId,
                        "ACC-TEST-02",
                        new BigDecimal("250.00"),
                        new BigDecimal("750.00"),
                        OffsetDateTime.parse("2026-08-14T11:00:00Z")
                )
        );

        mockMvc.perform(
                        get("/api/v1/ledger/accounts/{accountId}", accountId)
                                .param("page", "0")
                                .param("size", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.content[0].entryType").value("DEBIT"));
    }
}