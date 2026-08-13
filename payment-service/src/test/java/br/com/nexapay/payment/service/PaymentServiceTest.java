package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.CreatePixPaymentRequest;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldReturnExistingPaymentWhenIdempotencyKeyAlreadyExists() {
        PaymentMapper mapper = new PaymentMapper();

        PaymentService service = new PaymentService(
                paymentRepository,
                outboxEventRepository,
                mapper,
                new ObjectMapper().findAndRegisterModules()
        );

        Payment existing = new Payment(
                UUID.randomUUID(),
                "pedido-001",
                "ACC-1001",
                "cliente@email.com",
                new BigDecimal("250.00"),
                "Teste",
                PaymentStatus.PENDING,
                OffsetDateTime.now()
        );

        when(paymentRepository.findByIdempotencyKey("pedido-001"))
                .thenReturn(Optional.of(existing));

        var response = service.createPixPayment(
                "pedido-001",
                new CreatePixPaymentRequest(
                        "ACC-1001",
                        "cliente@email.com",
                        new BigDecimal("250.00"),
                        "Teste"
                )
        );

        assertThat(response.id()).isEqualTo(existing.getId());

        verify(paymentRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void shouldCreatePaymentAndOutboxEventInSameBusinessOperation() {
        PaymentMapper mapper = new PaymentMapper();

        PaymentService service = new PaymentService(
                paymentRepository,
                outboxEventRepository,
                mapper,
                new ObjectMapper().findAndRegisterModules()
        );

        when(paymentRepository.findByIdempotencyKey("pedido-002"))
                .thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createPixPayment(
                "pedido-002",
                new CreatePixPaymentRequest(
                        "ACC-2001",
                        "11999999999",
                        new BigDecimal("99.90"),
                        "PIX teste"
                )
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.amount()).isEqualByComparingTo("99.90");

        verify(paymentRepository).save(any(Payment.class));
        verify(outboxEventRepository).save(any());
    }
}
