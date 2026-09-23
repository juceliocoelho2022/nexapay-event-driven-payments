package br.com.nexapay.payment;

import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.exception.FraudReviewCaseConflictException;
import br.com.nexapay.payment.repository.FraudReviewCaseRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.service.FraudReviewQueueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "nexapay.outbox.fixed-delay-ms=600000",
        "nexapay.payment.scheduler.fixed-delay-ms=600000",
        "nexapay.payment.scheduler.initial-delay-ms=600000",
        "nexapay.payment.recurring.fixed-delay-ms=600000",
        "nexapay.payment.recurring.initial-delay-ms=600000",
        "nexapay.payment.fraud-review.lease-duration=15m"
})
class FraudReviewQueueIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_fraud_review_queue_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private FraudReviewQueueService service;

    @Autowired
    private FraudReviewCaseRepository caseRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        caseRepository.deleteAll();
        paymentRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void concurrentClaimsShouldHaveExactlyOneWinner() throws Exception {
        Payment payment = saveReviewPayment("claim-race");
        service.openCase(payment.getId(), OffsetDateTime.now());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<String> analystA = executor.submit(
                () -> claimWhenReleased(payment.getId(), "analyst-a", ready, start)
        );

        Future<String> analystB = executor.submit(
                () -> claimWhenReleased(payment.getId(), "analyst-b", ready, start)
        );

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<String> results = List.of(
                analystA.get(30, TimeUnit.SECONDS),
                analystB.get(30, TimeUnit.SECONDS)
        );

        assertThat(results).containsExactlyInAnyOrder("WIN", "CONFLICT");

        var reviewCase = caseRepository.findById(payment.getId()).orElseThrow();

        assertThat(reviewCase.getClaimedBy())
                .isIn("analyst-a", "analyst-b");
        assertThat(reviewCase.getClaimExpiresAt()).isNotNull();

        var queue = service.listOpenCases();

        assertThat(queue).hasSize(1);
        assertThat(queue.getFirst().available()).isFalse();
    }

    @Test
    void sameOwnerShouldRenewLease() {
        Payment payment = saveReviewPayment("renew");
        service.openCase(payment.getId(), OffsetDateTime.now());

        var first = service.claim(payment.getId(), "analyst-owner");
        var renewed = service.claim(payment.getId(), "analyst-owner");

        assertThat(renewed.claimedBy()).isEqualTo("analyst-owner");
        assertThat(renewed.claimExpiresAt())
                .isAfterOrEqualTo(first.claimExpiresAt());
    }

    @Test
    void expiredLeaseShouldAllowTakeover() {
        Payment payment = saveReviewPayment("expired");
        service.openCase(payment.getId(), OffsetDateTime.now());

        service.claim(payment.getId(), "expired-owner");

        OffsetDateTime expiredAt = OffsetDateTime.now().minusMinutes(1);
        int updated = jdbcTemplate.update(
                """
                UPDATE fraud_review_cases
                SET claim_expires_at = ?
                WHERE payment_id = ?
                """,
                expiredAt,
                payment.getId()
        );

        assertThat(updated).isEqualTo(1);

        var takeover = service.claim(
                payment.getId(),
                "new-owner"
        );

        assertThat(takeover.claimedBy()).isEqualTo("new-owner");
        assertThat(takeover.available()).isFalse();
    }

    @Test
    void releaseShouldReturnCaseToAvailableQueue() {
        Payment payment = saveReviewPayment("release");
        service.openCase(payment.getId(), OffsetDateTime.now());

        service.claim(payment.getId(), "analyst-owner");

        assertThatThrownBy(() ->
                service.release(payment.getId(), "other-analyst")
        ).isInstanceOf(FraudReviewCaseConflictException.class);

        var released = service.release(
                payment.getId(),
                "analyst-owner"
        );

        assertThat(released.claimedBy()).isNull();
        assertThat(released.claimExpiresAt()).isNull();
        assertThat(released.available()).isTrue();
    }

    private String claimWhenReleased(
            UUID paymentId,
            String reviewer,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Fraud review claim race start timed out");
        }

        try {
            service.claim(paymentId, reviewer);
            return "WIN";
        } catch (FraudReviewCaseConflictException conflict) {
            return "CONFLICT";
        }
    }

    private Payment saveReviewPayment(String suffix) {
        return paymentRepository.saveAndFlush(new Payment(
                UUID.randomUUID(),
                "fraud-review-queue-" + suffix,
                "ACC-QUEUE-" + suffix,
                suffix + "@nexapay.test",
                new BigDecimal("7500.00"),
                "Fraud review queue test",
                PaymentStatus.REVIEW,
                OffsetDateTime.now()
        ));
    }
}
