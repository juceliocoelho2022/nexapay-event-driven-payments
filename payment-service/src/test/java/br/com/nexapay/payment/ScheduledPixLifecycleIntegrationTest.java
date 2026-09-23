package br.com.nexapay.payment;

import br.com.nexapay.payment.api.SchedulePixPaymentRequest;
import br.com.nexapay.payment.domain.OutboxEvent;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.service.PaymentService;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "nexapay.outbox.fixed-delay-ms=600000",
        "nexapay.payment.scheduler.fixed-delay-ms=600000"
})
class ScheduledPixLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("nexapay_scheduled_pix_smoke_test")
            .withUsername("nexapay")
            .withPassword("nexapay");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void shouldCompleteScheduledPixLifecycleExactlyOnce() {
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusHours(1);

        SchedulePixPaymentRequest request = new SchedulePixPaymentRequest(
                "ACC-SMOKE-001",
                "scheduled-smoke@nexapay.test",
                new BigDecimal("199.90"),
                "Scheduled PIX smoke test",
                scheduledAt
        );

        var scheduled = paymentService.schedulePixPayment(
                "scheduled-smoke-001",
                request
        );

        assertThat(scheduled.status()).isEqualTo(PaymentStatus.SCHEDULED);
        assertThat(scheduled.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(scheduled.executedAt()).isNull();
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isZero();

        OffsetDateTime beforeDue = scheduledAt.minusSeconds(1);
        assertThat(paymentService.findDueScheduledPaymentIds(beforeDue, 10))
                .doesNotContain(scheduled.id());

        OffsetDateTime executionTime = scheduledAt.plusSeconds(1);
        assertThat(paymentService.findDueScheduledPaymentIds(executionTime, 10))
                .containsExactly(scheduled.id());

        boolean firstExecution = paymentService.executeDueScheduledPayment(
                scheduled.id(),
                executionTime
        );

        assertThat(firstExecution).isTrue();

        Payment executed = paymentRepository.findById(scheduled.id()).orElseThrow();
        assertThat(executed.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(executed.getScheduledAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(scheduledAt.truncatedTo(ChronoUnit.MILLIS));
        assertThat(executed.getExecutedAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(executionTime.truncatedTo(ChronoUnit.MILLIS));

        List<OutboxEvent> outbox = outboxEventRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.getFirst().getAggregateId()).isEqualTo(scheduled.id());
        assertThat(outbox.getFirst().getEventType()).isEqualTo("PaymentCreated");
        assertThat(outbox.getFirst().isPublished()).isFalse();

        boolean secondExecution = paymentService.executeDueScheduledPayment(
                scheduled.id(),
                executionTime.plusSeconds(1)
        );

        assertThat(secondExecution).isFalse();
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }
}
