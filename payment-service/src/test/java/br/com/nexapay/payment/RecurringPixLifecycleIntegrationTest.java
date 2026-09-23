package br.com.nexapay.payment;

import br.com.nexapay.payment.api.CreateRecurringPixRequest;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.RecurrenceFrequency;
import br.com.nexapay.payment.domain.RecurringPixSchedule;
import br.com.nexapay.payment.domain.RecurringScheduleStatus;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.repository.RecurringPixScheduleRepository;
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
import java.time.temporal.ChronoUnit;
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
class RecurringPixLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_recurring_pix_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RecurringPixScheduleService recurringService;

    @Autowired
    private RecurringPixScheduleRepository recurringRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
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
    void shouldMaterializeRecurringPixExactlyOncePerOccurrence() throws Exception {
        OffsetDateTime firstOccurrence = OffsetDateTime.now().plusHours(1);

        CreateRecurringPixRequest request = new CreateRecurringPixRequest(
                "ACC-REC-001",
                "recurring@nexapay.test",
                new BigDecimal("149.90"),
                "Assinatura recorrente",
                RecurrenceFrequency.DAILY,
                firstOccurrence,
                3
        );

        var created = recurringService.create("recurring-rule-001", request);
        var reused = recurringService.create("recurring-rule-001", request);

        assertThat(reused.id()).isEqualTo(created.id());
        assertThat(recurringRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();

        OffsetDateTime logicalNow = firstOccurrence.plusSeconds(1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> attempts = List.of(
                submitMaterialization(created.id(), logicalNow, ready, start),
                submitMaterialization(created.id(), logicalNow, ready, start)
        );

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        boolean first = attempts.get(0).get(30, TimeUnit.SECONDS);
        boolean second = attempts.get(1).get(30, TimeUnit.SECONDS);

        assertThat(List.of(first, second)).containsExactlyInAnyOrder(true, false);
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isZero();

        Payment firstPayment = paymentRepository.findAll().getFirst();
        assertThat(firstPayment.getRecurringScheduleId()).isEqualTo(created.id());
        assertThat(firstPayment.getRecurringOccurrenceAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(firstOccurrence.truncatedTo(ChronoUnit.MILLIS));

        RecurringPixSchedule afterFirst = recurringRepository.findById(created.id()).orElseThrow();
        assertThat(afterFirst.getRemainingOccurrences()).isEqualTo(2);
        assertThat(afterFirst.getStatus()).isEqualTo(RecurringScheduleStatus.ACTIVE);

        OffsetDateTime secondOccurrence = afterFirst.getNextOccurrenceAt();
        assertThat(recurringService.materializeNextOccurrence(
                created.id(),
                secondOccurrence.plusSeconds(1)
        )).isTrue();

        RecurringPixSchedule afterSecond = recurringRepository.findById(created.id()).orElseThrow();
        OffsetDateTime thirdOccurrence = afterSecond.getNextOccurrenceAt();

        assertThat(recurringService.materializeNextOccurrence(
                created.id(),
                thirdOccurrence.plusSeconds(1)
        )).isTrue();

        RecurringPixSchedule completed = recurringRepository.findById(created.id()).orElseThrow();

        assertThat(completed.getStatus()).isEqualTo(RecurringScheduleStatus.COMPLETED);
        assertThat(completed.getRemainingOccurrences()).isZero();
        assertThat(completed.getNextOccurrenceAt()).isNull();
        assertThat(paymentRepository.count()).isEqualTo(3);
        assertThat(outboxEventRepository.count()).isZero();
    }

    private Future<Boolean> submitMaterialization(
            java.util.UUID scheduleId,
            OffsetDateTime now,
            CountDownLatch ready,
            CountDownLatch start) {

        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent materialization start timed out");
            }
            return recurringService.materializeNextOccurrence(scheduleId, now);
        });
    }
}
