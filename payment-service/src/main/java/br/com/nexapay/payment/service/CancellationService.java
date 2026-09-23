package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.CancellationResponse;
import br.com.nexapay.payment.domain.CancellationAudit;
import br.com.nexapay.payment.domain.CancellationTargetType;
import br.com.nexapay.payment.exception.CancellationConflictException;
import br.com.nexapay.payment.exception.CancellationTargetNotFoundException;
import br.com.nexapay.payment.repository.CancellationAuditRepository;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.repository.RecurringPixScheduleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CancellationService {

    private final PaymentRepository paymentRepository;
    private final RecurringPixScheduleRepository recurringRepository;
    private final CancellationAuditRepository auditRepository;
    private final MeterRegistry meterRegistry;

    public CancellationService(
            PaymentRepository paymentRepository,
            RecurringPixScheduleRepository recurringRepository,
            CancellationAuditRepository auditRepository,
            MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.recurringRepository = recurringRepository;
        this.auditRepository = auditRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CancellationResponse cancelScheduledPayment(
            UUID paymentId,
            String reason,
            String actorSubject) {

        OffsetDateTime now = OffsetDateTime.now();

        int cancelled = paymentRepository.cancelScheduledPayment(
                paymentId,
                reason,
                now
        );

        if (cancelled == 0) {
            meterRegistry.counter("nexapay.payment.cancellation.rejected").increment();

            if (!paymentRepository.existsById(paymentId)) {
                throw new CancellationTargetNotFoundException("Pagamento", paymentId);
            }

            throw new CancellationConflictException("Pagamento", paymentId);
        }

        auditRepository.save(new CancellationAudit(
                UUID.randomUUID(),
                CancellationTargetType.PAYMENT,
                paymentId,
                reason,
                actorSubject,
                now,
                0
        ));

        meterRegistry.counter("nexapay.payment.cancellation.success").increment();

        return new CancellationResponse(
                CancellationTargetType.PAYMENT,
                paymentId,
                now,
                0
        );
    }

    @Transactional
    public CancellationResponse cancelRecurringSchedule(
            UUID scheduleId,
            String reason,
            String actorSubject) {

        OffsetDateTime now = OffsetDateTime.now();

        int cancelled = recurringRepository.cancelActiveSchedule(
                scheduleId,
                reason,
                now
        );

        if (cancelled == 0) {
            meterRegistry.counter(
                    "nexapay.payment.recurring.cancellation.rejected"
            ).increment();

            if (!recurringRepository.existsById(scheduleId)) {
                throw new CancellationTargetNotFoundException(
                        "Recorrência",
                        scheduleId
                );
            }

            throw new CancellationConflictException(
                    "Recorrência",
                    scheduleId
            );
        }

        int affectedPayments =
                paymentRepository.cancelScheduledPaymentsForRecurringSchedule(
                        scheduleId,
                        reason,
                        now
                );

        auditRepository.save(new CancellationAudit(
                UUID.randomUUID(),
                CancellationTargetType.RECURRING_SCHEDULE,
                scheduleId,
                reason,
                actorSubject,
                now,
                affectedPayments
        ));

        meterRegistry.counter(
                "nexapay.payment.recurring.cancellation.success"
        ).increment();

        if (affectedPayments > 0) {
            meterRegistry.counter(
                    "nexapay.payment.recurring.cancellation.materialized_payments"
            ).increment(affectedPayments);
        }

        return new CancellationResponse(
                CancellationTargetType.RECURRING_SCHEDULE,
                scheduleId,
                now,
                affectedPayments
        );
    }
}
