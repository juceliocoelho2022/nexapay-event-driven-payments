package br.com.nexapay.account;

import br.com.nexapay.account.api.AmountRequest;
import br.com.nexapay.account.api.CreateAccountRequest;
import br.com.nexapay.account.repository.OutboxEventRepository;
import br.com.nexapay.account.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class AccountOutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_accounts_outbox_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("nexapay.outbox.enabled", () -> "false");
        registry.add("nexapay.kafka.manage-topics", () -> "false");
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldPersistCreditAndDebitEventsInOutbox() {
        String accountNumber = "ACC-OUT-" + UUID.randomUUID().toString().substring(0, 12);
        var account = accountService.create(new CreateAccountRequest(accountNumber, "Outbox Test"));

        accountService.credit(account.id(), new AmountRequest(new BigDecimal("1000.00")));
        accountService.debit(account.id(), new AmountRequest(new BigDecimal("250.00")));

        var events = outboxEventRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();

        assertThat(events).hasSize(2);
        assertThat(events).extracting(event -> event.getEventType())
                .containsExactly("ACCOUNT_CREDITED", "ACCOUNT_DEBITED");
        assertThat(events).allMatch(event -> !event.isPublished());
    }
}
