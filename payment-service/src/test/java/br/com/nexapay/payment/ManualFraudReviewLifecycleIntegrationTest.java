package br.com.nexapay.payment;

import br.com.nexapay.payment.domain.ManualFraudReviewAudit;
import br.com.nexapay.payment.domain.ManualFraudReviewDecision;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.exception.FraudReviewCaseConflictException;
import br.com.nexapay.payment.exception.ManualFraudReviewConflictException;
import br.com.nexapay.payment.repository.FraudReviewCaseRepository;
import br.com.nexapay.payment.repository.ManualFraudReviewAuditRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.service.FraudReviewQueueService;
import br.com.nexapay.payment.service.ManualFraudReviewService;
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
        "nexapay.payment.recurring.initial-delay-ms=600000"
})
class ManualFraudReviewLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_manual_fraud_review_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ManualFraudReviewService service;

    @Autowired
    private FraudReviewQueueService queueService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FraudReviewCaseRepository reviewCaseRepository;

    @Autowired
    private ManualFraudReviewAuditRepository auditRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        reviewCaseRepository.deleteAll();
        paymentRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldApproveAndRejectPaymentsOwnedByReviewer() {
        Payment approvePayment = saveReviewPayment("approve");
        prepareOwnedCase(approvePayment.getId(), "fraud-analyst-01");

        var approved = service.review(
                approvePayment.getId(),
                ManualFraudReviewDecision.APPROVE,
                "Documentação validada",
                "fraud-analyst-01"
        );

        assertThat(approved.previousStatus()).isEqualTo(PaymentStatus.REVIEW);
        assertThat(approved.finalStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentRepository.findById(approvePayment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(reviewCaseRepository.findById(approvePayment.getId()).orElseThrow().getStatus().name())
                .isEqualTo("RESOLVED");

        Payment rejectPayment = saveReviewPayment("reject");
        prepareOwnedCase(rejectPayment.getId(), "fraud-analyst-02");

        var rejected = service.review(
                rejectPayment.getId(),
                ManualFraudReviewDecision.REJECT,
                "Inconsistência cadastral confirmada",
                "fraud-analyst-02"
        );

        assertThat(rejected.finalStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(paymentRepository.findById(rejectPayment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REJECTED);
        assertThat(auditRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldRejectManualReviewWithoutActiveOwnership() {
        Payment payment = saveReviewPayment("not-owner");
        queueService.openCase(payment.getId(), OffsetDateTime.now());
        queueService.claim(payment.getId(), "actual-owner");

        assertThatThrownBy(() -> service.review(
                payment.getId(),
                ManualFraudReviewDecision.APPROVE,
                "Reviewer sem ownership",
                "other-reviewer"
        )).isInstanceOf(FraudReviewCaseConflictException.class);

        assertThat(auditRepository.count()).isZero();
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REVIEW);
        assertThat(reviewCaseRepository.findById(payment.getId()).orElseThrow().getStatus().name())
                .isEqualTo("OPEN");
    }

    @Test
    void shouldRejectManualReviewWhenPaymentIsNotInReview() {
        Payment pending = savePayment("pending", PaymentStatus.PENDING);

        assertThatThrownBy(() -> service.review(
                pending.getId(),
                ManualFraudReviewDecision.APPROVE,
                "Tentativa inválida",
                "fraud-analyst-03"
        )).isInstanceOf(ManualFraudReviewConflictException.class);

        assertThat(auditRepository.count()).isZero();
        assertThat(paymentRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void concurrentApproveAndRejectByOwnerShouldHaveExactlyOneWinner() throws Exception {
        Payment payment = saveReviewPayment("race");
        prepareOwnedCase(payment.getId(), "reviewer-owner");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<String> approve = executor.submit(() -> reviewWhenReleased(
                payment.getId(),
                ManualFraudReviewDecision.APPROVE,
                "Aprovação concorrente",
                "reviewer-owner",
                ready,
                start
        ));

        Future<String> reject = executor.submit(() -> reviewWhenReleased(
                payment.getId(),
                ManualFraudReviewDecision.REJECT,
                "Rejeição concorrente",
                "reviewer-owner",
                ready,
                start
        ));

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<String> results = List.of(
                approve.get(30, TimeUnit.SECONDS),
                reject.get(30, TimeUnit.SECONDS)
        );

        assertThat(results).containsExactlyInAnyOrder("WIN", "CONFLICT");
        assertThat(auditRepository.count()).isEqualTo(1);

        Payment finalPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        ManualFraudReviewAudit audit = auditRepository.findAll().getFirst();

        assertThat(finalPayment.getStatus()).isEqualTo(audit.getFinalStatus());
        assertThat(audit.getPreviousStatus()).isEqualTo(PaymentStatus.REVIEW);
        assertThat(reviewCaseRepository.findById(payment.getId()).orElseThrow().getStatus().name())
                .isEqualTo("RESOLVED");
    }

    private String reviewWhenReleased(
            UUID paymentId,
            ManualFraudReviewDecision decision,
            String reason,
            String reviewer,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Manual review race start timed out");
        }

        try {
            service.review(paymentId, decision, reason, reviewer);
            return "WIN";
        } catch (RuntimeException conflict) {
            return "CONFLICT";
        }
    }

    private void prepareOwnedCase(UUID paymentId, String reviewer) {
        queueService.openCase(paymentId, OffsetDateTime.now());
        queueService.claim(paymentId, reviewer);
    }

    private Payment saveReviewPayment(String suffix) {
        return savePayment(suffix, PaymentStatus.REVIEW);
    }

    private Payment savePayment(String suffix, PaymentStatus status) {
        return paymentRepository.saveAndFlush(new Payment(
                UUID.randomUUID(),
                "manual-review-" + suffix,
                "ACC-MANUAL-" + suffix,
                suffix + "@nexapay.test",
                new BigDecimal("7500.00"),
                "Manual fraud review test",
                status,
                OffsetDateTime.now()
        ));
    }
}
