package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.CreatePixPaymentRequest;
import br.com.nexapay.payment.api.SchedulePixPaymentRequest;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldReturnExistingPaymentWhenIdempotencyKeyAlreadyExists() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentService service = newService(registry);

        Payment existing = payment(
                UUID.randomUUID(),
                "pedido-001",
                "ACC-1001",
                "cliente@email.com",
                "250.00"
        );

        when(paymentRepository.findByIdempotencyKey("pedido-001"))
                .thenReturn(Optional.of(existing));

        var response = service.createPixPayment(
                "pedido-001",
                request("ACC-1001", "cliente@email.com", "250.00", "Teste")
        );

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(registry.counter("nexapay.payment.idempotency.reused", "source", "precheck").count())
                .isEqualTo(1.0);

        verify(paymentRepository, never()).insertIfIdempotencyKeyAbsent(
                any(), anyString(), anyString(), anyString(), any(), any(), anyString(), any()
        );
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldCreatePaymentAndOutboxWhenAtomicReservationWins() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentService service = newService(registry);

        when(paymentRepository.findByIdempotencyKey("pedido-002"))
                .thenReturn(Optional.empty());
        when(paymentRepository.insertIfIdempotencyKeyAbsent(
                any(), eq("pedido-002"), anyString(), anyString(), any(), any(), eq("PENDING"), any()
        )).thenReturn(1);

        var response = service.createPixPayment(
                "pedido-002",
                request("ACC-2001", "11999999999", "99.90", "PIX teste")
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.amount()).isEqualByComparingTo("99.90");
        assertThat(response.scheduledAt()).isNull();
        assertThat(registry.counter("nexapay.payment.created").count()).isEqualTo(1.0);

        verify(paymentRepository).insertIfIdempotencyKeyAbsent(
                any(), eq("pedido-002"), eq("ACC-2001"), eq("11999999999"),
                eq(new BigDecimal("99.90")), eq("PIX teste"), eq("PENDING"), any()
        );
        verify(outboxEventRepository).save(any());
    }

    @Test
    void shouldLoadWinnerWhenConcurrentReservationLoses() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentService service = newService(registry);

        Payment winner = payment(
                UUID.randomUUID(),
                "pedido-race",
                "ACC-3001",
                "race@nexapay.test",
                "120.00"
        );

        when(paymentRepository.findByIdempotencyKey("pedido-race"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(paymentRepository.insertIfIdempotencyKeyAbsent(
                any(), eq("pedido-race"), anyString(), anyString(), any(), any(), eq("PENDING"), any()
        )).thenReturn(0);

        var response = service.createPixPayment(
                "pedido-race",
                request("ACC-3001", "race@nexapay.test", "120.00", "concorrencia")
        );

        assertThat(response.id()).isEqualTo(winner.getId());
        assertThat(registry.counter("nexapay.payment.idempotency.reused", "source", "concurrent_conflict").count())
                .isEqualTo(1.0);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldSchedulePaymentWithoutCreatingOutbox() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentService service = newService(registry);
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusHours(2);

        when(paymentRepository.findByIdempotencyKey("schedule-001"))
                .thenReturn(Optional.empty());
        when(paymentRepository.insertScheduledIfIdempotencyKeyAbsent(
                any(),
                eq("schedule-001"),
                eq("ACC-4001"),
                eq("scheduled@nexapay.test"),
                eq(new BigDecimal("350.00")),
                eq("PIX agendado"),
                any(),
                eq(scheduledAt)
        )).thenReturn(1);

        var response = service.schedulePixPayment(
                "schedule-001",
                scheduleRequest(
                        "ACC-4001",
                        "scheduled@nexapay.test",
                        "350.00",
                        "PIX agendado",
                        scheduledAt
                )
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.SCHEDULED);
        assertThat(response.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(response.executedAt()).isNull();
        assertThat(registry.counter("nexapay.payment.scheduled").count()).isEqualTo(1.0);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingScheduledPaymentWhenIdempotencyKeyAlreadyExists() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentService service = newService(registry);

        OffsetDateTime scheduledAt = OffsetDateTime.now().plusHours(3);
        Payment existing = new Payment(
                UUID.randomUUID(),
                "schedule-reused",
                "ACC-4501",
                "reused@nexapay.test",
                new BigDecimal("210.00"),
                "PIX agendado existente",
                PaymentStatus.SCHEDULED,
                OffsetDateTime.now(),
                scheduledAt,
                null
        );

        when(paymentRepository.findByIdempotencyKey("schedule-reused"))
                .thenReturn(Optional.of(existing));

        var response = service.schedulePixPayment(
                "schedule-reused",
                scheduleRequest(
                        "ACC-4501",
                        "reused@nexapay.test",
                        "210.00",
                        "PIX agendado existente",
                        scheduledAt
                )
        );

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(PaymentStatus.SCHEDULED);
        assertThat(registry.counter(
                "nexapay.payment.idempotency.reused",
                "source",
                "scheduled_precheck"
        ).count()).isEqualTo(1.0);

        verify(paymentRepository, never()).insertScheduledIfIdempotencyKeyAbsent(
                any(), anyString(), anyString(), anyString(), any(), any(), any(), any()
        );
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldCreateOutboxOnlyWhenScheduledPaymentClaimWins() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentService service = newService(registry);

        UUID paymentId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now().minusDays(1);
        OffsetDateTime scheduledAt = OffsetDateTime.now().minusMinutes(1);
        OffsetDateTime executionTime = OffsetDateTime.now();

        Payment claimedPayment = new Payment(
                paymentId,
                "schedule-002",
                "ACC-5001",
                "due@nexapay.test",
                new BigDecimal("75.00"),
                "PIX vencido",
                PaymentStatus.PENDING,
                createdAt,
                scheduledAt,
                executionTime
        );

        when(paymentRepository.claimScheduledPaymentForExecution(paymentId, executionTime))
                .thenReturn(1);
        when(paymentRepository.findById(paymentId))
                .thenReturn(Optional.of(claimedPayment));

        boolean executed = service.executeDueScheduledPayment(paymentId, executionTime);

        assertThat(executed).isTrue();
        assertThat(registry.counter("nexapay.payment.scheduled.executed").count()).isEqualTo(1.0);
        verify(outboxEventRepository).save(any());
    }

    @Test
    void shouldSkipOutboxWhenAnotherSchedulerAlreadyClaimedPayment() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentService service = newService(registry);

        UUID paymentId = UUID.randomUUID();
        OffsetDateTime executionTime = OffsetDateTime.now();

        when(paymentRepository.claimScheduledPaymentForExecution(paymentId, executionTime))
                .thenReturn(0);

        boolean executed = service.executeDueScheduledPayment(paymentId, executionTime);

        assertThat(executed).isFalse();
        assertThat(registry.counter("nexapay.payment.scheduled.claim.skipped").count()).isEqualTo(1.0);
        verify(paymentRepository, never()).findById(paymentId);
        verify(outboxEventRepository, never()).save(any());
    }

    private PaymentService newService(SimpleMeterRegistry registry) {
        return new PaymentService(
                paymentRepository,
                outboxEventRepository,
                new PaymentMapper(),
                new ObjectMapper().findAndRegisterModules(),
                registry,
                Tracer.NOOP,
                Propagator.NOOP
        );
    }

    private CreatePixPaymentRequest request(
            String payerAccountId,
            String pixKey,
            String amount,
            String description
    ) {
        return new CreatePixPaymentRequest(
                payerAccountId,
                pixKey,
                new BigDecimal(amount),
                description
        );
    }

    private SchedulePixPaymentRequest scheduleRequest(
            String payerAccountId,
            String pixKey,
            String amount,
            String description,
            OffsetDateTime scheduledAt
    ) {
        return new SchedulePixPaymentRequest(
                payerAccountId,
                pixKey,
                new BigDecimal(amount),
                description,
                scheduledAt
        );
    }

    private Payment payment(
            UUID id,
            String idempotencyKey,
            String payerAccountId,
            String pixKey,
            String amount
    ) {
        return new Payment(
                id,
                idempotencyKey,
                payerAccountId,
                pixKey,
                new BigDecimal(amount),
                "Teste",
                PaymentStatus.PENDING,
                OffsetDateTime.now()
        );
    }
}
