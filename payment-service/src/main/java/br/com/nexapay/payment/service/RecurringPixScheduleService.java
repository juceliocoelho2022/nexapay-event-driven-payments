package br.com.nexapay.payment.service;

import br.com.nexapay.payment.api.CreateRecurringPixRequest;
import br.com.nexapay.payment.api.RecurringPixScheduleResponse;
import br.com.nexapay.payment.domain.RecurringPixSchedule;
import br.com.nexapay.payment.domain.RecurringScheduleStatus;
import br.com.nexapay.payment.repository.PaymentRepository;
import br.com.nexapay.payment.repository.RecurringPixScheduleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RecurringPixScheduleService {

    private final RecurringPixScheduleRepository recurringRepository;
    private final PaymentRepository paymentRepository;
    private final RecurrenceCalculator recurrenceCalculator;
    private final MeterRegistry meterRegistry;

    public RecurringPixScheduleService(
            RecurringPixScheduleRepository recurringRepository,
            PaymentRepository paymentRepository,
            RecurrenceCalculator recurrenceCalculator,
            MeterRegistry meterRegistry) {
        this.recurringRepository = recurringRepository;
        this.paymentRepository = paymentRepository;
        this.recurrenceCalculator = recurrenceCalculator;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public RecurringPixScheduleResponse create(
            String idempotencyKey,
            CreateRecurringPixRequest request) {

        return recurringRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    meterRegistry.counter(
                            "nexapay.payment.idempotency.reused",
                            "source",
                            "recurring_precheck"
                    ).increment();
                    return toResponse(existing);
                })
                .orElseGet(() -> createOrLoad(idempotencyKey, request));
    }

    @Transactional(readOnly = true)
    public List<UUID> findDueScheduleIds(OffsetDateTime now, int batchSize) {
        return recurringRepository.findDueScheduleIds(now, batchSize);
    }

    @Transactional
    public boolean materializeNextOccurrence(
            UUID scheduleId,
            OffsetDateTime now) {

        RecurringPixSchedule schedule = recurringRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalStateException(
                        "Recurring PIX schedule not found: " + scheduleId
                ));

        OffsetDateTime occurrenceAt = schedule.getNextOccurrenceAt();

        if (schedule.getStatus() != RecurringScheduleStatus.ACTIVE
                || occurrenceAt == null
                || occurrenceAt.isAfter(now)
                || schedule.getRemainingOccurrences() <= 0) {
            meterRegistry.counter("nexapay.payment.recurring.claim.skipped").increment();
            return false;
        }

        int claimed;

        if (schedule.getRemainingOccurrences() == 1) {
            claimed = recurringRepository.completeOccurrence(
                    scheduleId,
                    occurrenceAt,
                    now
            );
        } else {
            OffsetDateTime nextOccurrence = recurrenceCalculator.next(
                    occurrenceAt,
                    schedule.getFrequency(),
                    schedule.getAnchorDay()
            );

            claimed = recurringRepository.advanceOccurrence(
                    scheduleId,
                    occurrenceAt,
                    nextOccurrence,
                    schedule.getRemainingOccurrences() - 1,
                    now
            );
        }

        if (claimed == 0) {
            meterRegistry.counter("nexapay.payment.recurring.claim.skipped").increment();
            return false;
        }

        UUID paymentId = UUID.randomUUID();
        String occurrenceIdempotencyKey = occurrenceIdempotencyKey(
                scheduleId,
                occurrenceAt
        );

        int inserted = paymentRepository.insertRecurringScheduledIfAbsent(
                paymentId,
                occurrenceIdempotencyKey,
                schedule.getPayerAccountId(),
                schedule.getPixKey(),
                schedule.getAmount(),
                schedule.getDescription(),
                now,
                occurrenceAt,
                scheduleId,
                occurrenceAt
        );

        if (inserted == 1) {
            meterRegistry.counter("nexapay.payment.recurring.materialized").increment();
        } else {
            meterRegistry.counter(
                    "nexapay.payment.idempotency.reused",
                    "source",
                    "recurring_occurrence"
            ).increment();
        }

        if (schedule.getRemainingOccurrences() == 1) {
            meterRegistry.counter("nexapay.payment.recurring.completed").increment();
        }

        return true;
    }

    private RecurringPixScheduleResponse createOrLoad(
            String idempotencyKey,
            CreateRecurringPixRequest request) {

        OffsetDateTime now = OffsetDateTime.now();
        UUID scheduleId = UUID.randomUUID();

        int inserted = recurringRepository.insertIfIdempotencyKeyAbsent(
                scheduleId,
                idempotencyKey,
                request.payerAccountId(),
                request.pixKey(),
                request.amount(),
                request.description(),
                request.frequency().name(),
                request.firstOccurrenceAt(),
                request.occurrences(),
                request.firstOccurrenceAt().getDayOfMonth(),
                now
        );

        RecurringPixSchedule schedule = recurringRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Recurring PIX idempotency key was claimed but schedule could not be loaded: "
                                + idempotencyKey
                ));

        if (inserted == 1) {
            meterRegistry.counter("nexapay.payment.recurring.created").increment();
        } else {
            meterRegistry.counter(
                    "nexapay.payment.idempotency.reused",
                    "source",
                    "recurring_concurrent_conflict"
            ).increment();
        }

        return toResponse(schedule);
    }

    private String occurrenceIdempotencyKey(
            UUID scheduleId,
            OffsetDateTime occurrenceAt) {
        return "recurring:"
                + scheduleId
                + ":"
                + occurrenceAt.toInstant().toEpochMilli();
    }

    private RecurringPixScheduleResponse toResponse(
            RecurringPixSchedule schedule) {
        return new RecurringPixScheduleResponse(
                schedule.getId(),
                schedule.getPayerAccountId(),
                schedule.getPixKey(),
                schedule.getAmount(),
                schedule.getDescription(),
                schedule.getFrequency(),
                schedule.getStatus(),
                schedule.getNextOccurrenceAt(),
                schedule.getRemainingOccurrences(),
                schedule.getCreatedAt()
        );
    }
}
