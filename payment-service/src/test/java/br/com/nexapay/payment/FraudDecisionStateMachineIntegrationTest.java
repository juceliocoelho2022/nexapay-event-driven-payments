package br.com.nexapay.payment;

import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.event.FraudDecisionMadeEvent;
import br.com.nexapay.payment.repository.FraudReviewCaseRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.repository.ProcessedFraudDecisionEventRepository;
import br.com.nexapay.payment.service.FraudDecisionStateMachineService;
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
class FraudDecisionStateMachineIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_fraud_state_machine_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private FraudDecisionStateMachineService service;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProcessedFraudDecisionEventRepository processedRepository;

    @Autowired
    private FraudReviewCaseRepository reviewCaseRepository;

    @BeforeEach
    void cleanDatabase() {
        reviewCaseRepository.deleteAll();
        processedRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void shouldApplyApprovedReviewAndBlockedDecisions() {
        assertTransition("APPROVED", PaymentStatus.COMPLETED, 20);
        clear();
        assertTransition("REVIEW", PaymentStatus.REVIEW, 70);
        clear();
        assertTransition("BLOCKED", PaymentStatus.REJECTED, 95);
    }

    @Test
    void shouldIgnoreDuplicateDecisionEvent() {
        Payment payment = savePendingPayment("duplicate");
        FraudDecisionMadeEvent event = event(
                payment.getId(),
                "APPROVED",
                20
        );

        assertThat(service.apply(event)).isTrue();
        assertThat(service.apply(event)).isFalse();

        Payment finalPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(processedRepository.count()).isEqualTo(1);
        assertThat(reviewCaseRepository.existsById(payment.getId())).isFalse();
    }

    @Test
    void shouldHandleConcurrentDuplicateDeliveryExactlyOnce() throws Exception {
        Payment payment = savePendingPayment("concurrent");
        FraudDecisionMadeEvent event = event(
                payment.getId(),
                "APPROVED",
                20
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> first = executor.submit(
                    () -> invokeWhenReleased(event, ready, start)
            );
            Future<Boolean> second = executor.submit(
                    () -> invokeWhenReleased(event, ready, start)
            );

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(processedRepository.count()).isEqualTo(1);
        assertThat(
                paymentRepository.findById(payment.getId()).orElseThrow().getStatus()
        ).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void shouldRollbackReservationWhenPaymentIsNotPending() {
        Payment payment = savePendingPayment("invalid-state");

        assertThat(service.apply(event(
                payment.getId(),
                "APPROVED",
                20
        ))).isTrue();

        long processedBefore = processedRepository.count();

        FraudDecisionMadeEvent unexpectedSecondDecision = new FraudDecisionMadeEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                payment.getId(),
                "BLOCKED",
                95,
                "Unexpected second decision",
                OffsetDateTime.now()
        );

        assertThatThrownBy(() -> service.apply(unexpectedSecondDecision))
                .isInstanceOf(IllegalStateException.class);

        assertThat(processedRepository.count()).isEqualTo(processedBefore);
        assertThat(
                paymentRepository.findById(payment.getId()).orElseThrow().getStatus()
        ).isEqualTo(PaymentStatus.COMPLETED);
    }

    private void assertTransition(
            String decision,
            PaymentStatus expected,
            int riskScore) {

        Payment payment = savePendingPayment(decision.toLowerCase());

        assertThat(service.apply(event(
                payment.getId(),
                decision,
                riskScore
        ))).isTrue();

        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(expected);
        assertThat(updated.getFraudDecision()).isEqualTo(decision);
        assertThat(updated.getFraudRiskScore()).isEqualTo(riskScore);
        assertThat(updated.getFraudReason()).contains(decision);
        assertThat(updated.getFraudDecidedAt()).isNotNull();
        assertThat(processedRepository.count()).isEqualTo(1);

        if (expected == PaymentStatus.REVIEW) {
            assertThat(reviewCaseRepository.existsById(payment.getId())).isTrue();
        } else {
            assertThat(reviewCaseRepository.existsById(payment.getId())).isFalse();
        }
    }

    private Payment savePendingPayment(String suffix) {
        return paymentRepository.saveAndFlush(new Payment(
                UUID.randomUUID(),
                "fraud-state-" + suffix,
                "ACC-FRAUD-" + suffix,
                suffix + "@nexapay.test",
                new BigDecimal("100.00"),
                "Fraud state machine test",
                PaymentStatus.PENDING,
                OffsetDateTime.now()
        ));
    }

    private FraudDecisionMadeEvent event(
            UUID paymentId,
            String decision,
            int riskScore) {

        return new FraudDecisionMadeEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                paymentId,
                decision,
                riskScore,
                decision + " fraud decision",
                OffsetDateTime.now()
        );
    }

    private boolean invokeWhenReleased(
            FraudDecisionMadeEvent event,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Concurrent fraud decision test start timed out"
            );
        }

        return service.apply(event);
    }

    private void clear() {
        reviewCaseRepository.deleteAll();
        processedRepository.deleteAll();
        paymentRepository.deleteAll();
    }
}
