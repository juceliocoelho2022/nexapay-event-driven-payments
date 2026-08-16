package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.CreatePixPaymentRequest;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        PaymentService service = newService();

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

        verify(paymentRepository, never()).insertIfIdempotencyKeyAbsent(
                any(), anyString(), anyString(), anyString(), any(), any(), anyString(), any()
        );
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldCreatePaymentAndOutboxWhenAtomicReservationWins() {
        PaymentService service = newService();

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

        verify(paymentRepository).insertIfIdempotencyKeyAbsent(
                any(), eq("pedido-002"), eq("ACC-2001"), eq("11999999999"),
                eq(new BigDecimal("99.90")), eq("PIX teste"), eq("PENDING"), any()
        );
        verify(outboxEventRepository).save(any());
    }

    @Test
    void shouldLoadWinnerWhenConcurrentReservationLoses() {
        PaymentService service = newService();

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
        verify(outboxEventRepository, never()).save(any());
    }

    private PaymentService newService() {
        return new PaymentService(
                paymentRepository,
                outboxEventRepository,
                new PaymentMapper(),
                new ObjectMapper().findAndRegisterModules()
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
