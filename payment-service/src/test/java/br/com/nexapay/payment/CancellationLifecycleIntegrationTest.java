package br.com.nexapay.payment;

import br.com.nexapay.payment.api.CreateRecurringPixRequest;
import br.com.nexapay.payment.api.SchedulePixPaymentRequest;
import br.com.nexapay.payment.domain.CancellationAudit;
import br.com.nexapay.payment.domain.CancellationTargetType;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.domain.RecurrenceFrequency;
import br.com.nexapay.payment.domain.RecurringPixSchedule;
import br.com.nexapay.payment.domain.RecurringScheduleStatus;
import br.com.nexapay.payment.exception.CancellationConflictException;
import br.com.nexapay.payment.repository.CancellationAuditRepository;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.repository.RecurringPixScheduleRepository;
import br.com.nexapay.payment.service.CancellationService;
import br.com.nexapay.payment.service.PaymentService;
import br.com.nexapay.payment.service.RecurringPixScheduleService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "nexapay.outbox.fixed-delay-ms=600000",
        "nexapay.payment.scheduler.fixed-delay-ms=600000",
        "nexapay.payment.scheduler.initial-delay-ms=600000",
        "nexapay.payment.recurring.fixed-delay-ms=600000",
        "nexapay.payment.recurring.initial-delay-ms=600000"
})
class CancellationLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_cancellation_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CancellationService cancellationService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RecurringPixScheduleService recurringService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RecurringPixScheduleRepository recurringRepository;

    @Autowired
    private CancellationAuditRepository auditRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
        recurringRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void cancellationAndExecutionShouldHaveExactlyOneWinner() throws Exception {
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusHours(1);

        var scheduled = paymentService.schedulePixPayment(
                "cancel-race-payment",
                new SchedulePixPaymentRequest(
                        "ACC-CANCEL-001",
                        "cancel-race@nexapay.test",
                        new BigDecimal("275.00"),
                        "Cancel versus execute race",
                        scheduledAt
                )
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> cancelAttempt = executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                cancellationService.cancelScheduledPayment(
                        scheduled.id(),
                        "Cliente desistiu",
                        "user-cancel-race"
                );
                return true;
            } catch (CancellationConflictException conflict) {
                return false;
            }
        });

        Future<Boolean> executeAttempt = executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return paymentService.executeDueScheduledPayment(
                    scheduled.id(),
                    scheduledAt.plusSeconds(1)
            );
        });

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        boolean cancelled = cancelAttempt.get(30, TimeUnit.SECONDS);
        boolean executed = executeAttempt.get(30, TimeUnit.SECONDS);

        assertThat(cancelled ^ executed).isTrue();

        Payment finalPayment = paymentRepository.findById(scheduled.id()).orElseThrow();

        if (cancelled) {
            assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(outboxEventRepository.count()).isZero();

            CancellationAudit audit = auditRepository.findAll().getFirst();
            assertThat(audit.getTargetType()).isEqualTo(CancellationTargetType.PAYMENT);
            assertThat(audit.getTargetId()).isEqualTo(scheduled.id());
            assertThat(audit.getActorSubject()).isEqualTo("user-cancel-race");
        } else {
            assertThat(finalPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(outboxEventRepository.count()).isEqualTo(1);
            assertThat(auditRepository.count()).isZero();
        }
    }

    @Test
    void shouldCancelRecurringScheduleAndMaterializedScheduledPayments() {
        OffsetDateTime firstOccurrence = OffsetDateTime.now().plusHours(2);

        var recurring = recurringService.create(
                "cancel-recurring-rule",
                new CreateRecurringPixRequest(
                        "ACC-CANCEL-REC",
                        "cancel-recurring@nexapay.test",
                        new BigDecimal("99.90"),
                        "Assinatura cancelável",
                        RecurrenceFrequency.DAILY,
                        firstOccurrence,
                        3
                )
        );

        assertThat(recurringService.materializeNextOccurrence(
                recurring.id(),
                firstOccurrence.plusSeconds(1)
        )).isTrue();

        assertThat(paymentRepository.count()).isEqualTo(1);

        Payment materialized = paymentRepository.findAll().getFirst();
        assertThat(materialized.getStatus()).isEqualTo(PaymentStatus.SCHEDULED);

        var cancelled = cancellationService.cancelRecurringSchedule(
                recurring.id(),
                "Assinatura encerrada",
                "user-recurring-cancel"
        );

        assertThat(cancelled.affectedScheduledPayments()).isEqualTo(1);

        RecurringPixSchedule schedule =
                recurringRepository.findById(recurring.id()).orElseThrow();

        assertThat(schedule.getStatus()).isEqualTo(RecurringScheduleStatus.CANCELLED);
        assertThat(schedule.getNextOccurrenceAt()).isNull();

        Payment cancelledPayment =
                paymentRepository.findById(materialized.getId()).orElseThrow();

        assertThat(cancelledPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(outboxEventRepository.count()).isZero();

        List<CancellationAudit> audits = auditRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getTargetType())
                .isEqualTo(CancellationTargetType.RECURRING_SCHEDULE);
        assertThat(audits.getFirst().getAffectedScheduledPayments()).isEqualTo(1);

        assertThat(recurringService.materializeNextOccurrence(
                recurring.id(),
                firstOccurrence.plusDays(2)
        )).isFalse();

        assertThat(paymentRepository.count()).isEqualTo(1);
    }
}
