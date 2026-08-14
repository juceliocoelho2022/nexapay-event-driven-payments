package br.com.nexapay.account;

import br.com.nexapay.account.api.AmountRequest;
import br.com.nexapay.account.api.CreateAccountRequest;
import br.com.nexapay.account.exception.InsufficientBalanceException;
import br.com.nexapay.account.service.AccountService;
import org.junit.jupiter.api.AfterEach;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class AccountConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_accounts_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AccountService accountService;

    private ExecutorService executor;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);

        String accountNumber = "ACC-CONC-" + UUID.randomUUID().toString().substring(0, 12);
        var account = accountService.create(new CreateAccountRequest(accountNumber, "Concurrency Test"));
        accountId = account.id();

        accountService.credit(accountId, new AmountRequest(new BigDecimal("750.00")));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldAllowOnlyOneConcurrentDebitWhenBalanceSupportsOnlyOneOperation() throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);

        Callable<Boolean> debitTask = () -> {
            startGate.await(5, TimeUnit.SECONDS);

            try {
                accountService.debit(accountId, new AmountRequest(new BigDecimal("500.00")));
                return true;
            } catch (InsufficientBalanceException exception) {
                return false;
            }
        };

        Future<Boolean> firstDebit = executor.submit(debitTask);
        Future<Boolean> secondDebit = executor.submit(debitTask);

        startGate.countDown();

        boolean firstSucceeded = firstDebit.get(10, TimeUnit.SECONDS);
        boolean secondSucceeded = secondDebit.get(10, TimeUnit.SECONDS);

        long successfulDebits = 0;
        if (firstSucceeded) {
            successfulDebits++;
        }
        if (secondSucceeded) {
            successfulDebits++;
        }

        var finalAccount = accountService.getById(accountId);

        assertThat(successfulDebits).isEqualTo(1);
        assertThat(finalAccount.balance()).isEqualByComparingTo("250.00");
    }
}
