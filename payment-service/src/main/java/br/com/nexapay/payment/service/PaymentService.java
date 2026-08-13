package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.CreatePixPaymentRequest;
import br.com.nexapay.payment.api.PaymentResponse;
import br.com.nexapay.payment.domain.OutboxEvent;
import br.com.nexapay.payment.domain.Payment;
import br.com.nexapay.payment.domain.PaymentStatus;
import br.com.nexapay.payment.event.PaymentCreatedEvent;
import br.com.nexapay.payment.exception.PaymentNotFoundException;
import br.com.nexapay.payment.repository.OutboxEventRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            PaymentMapper paymentMapper,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentMapper = paymentMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentResponse createPixPayment(
            String idempotencyKey,
            CreatePixPaymentRequest request) {

        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(paymentMapper::toResponse)
                .orElseGet(() -> createNewPayment(idempotencyKey, request));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        return paymentMapper.toResponse(payment);
    }

    private PaymentResponse createNewPayment(
            String idempotencyKey,
            CreatePixPaymentRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        UUID paymentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Payment payment = new Payment(
                paymentId,
                idempotencyKey,
                request.payerAccountId(),
                request.pixKey(),
                request.amount(),
                request.description(),
                PaymentStatus.PENDING,
                now
        );

        PaymentCreatedEvent event = new PaymentCreatedEvent(
                eventId,
                paymentId,
                request.payerAccountId(),
                request.pixKey(),
                request.amount(),
                now
        );

        Payment saved = paymentRepository.save(payment);

        OutboxEvent outbox = new OutboxEvent(
                eventId,
                paymentId,
                "PaymentCreated",
                toJson(event),
                false,
                now
        );

        outboxEventRepository.save(outbox);

        return paymentMapper.toResponse(saved);
    }

    private String toJson(PaymentCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento de pagamento", e);
        }
    }
}
