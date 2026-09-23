package br.com.nexapay.payment.observability;

import br.com.nexapay.payment.domain.FraudReviewCaseStatus;
import br.com.nexapay.payment.domain.FraudReviewPriority;
import br.com.nexapay.payment.repository.FraudReviewCaseRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FraudReviewSlaMonitor {

    private final FraudReviewCaseRepository repository;
    private final MeterRegistry meterRegistry;

    private final AtomicLong openCases = new AtomicLong();
    private final AtomicLong overdueCases = new AtomicLong();
    private final AtomicLong overdueP1Cases = new AtomicLong();
    private final AtomicLong p1Cases = new AtomicLong();
    private final AtomicLong p2Cases = new AtomicLong();
    private final AtomicLong p3Cases = new AtomicLong();
    private final AtomicLong oldestOpenAgeSeconds = new AtomicLong();

    public FraudReviewSlaMonitor(
            FraudReviewCaseRepository repository,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;

        Gauge.builder(
                        "nexapay.payment.fraud_review_sla.open_cases",
                        openCases,
                        AtomicLong::get
                )
                .description("Open fraud review cases")
                .register(meterRegistry);

        Gauge.builder(
                        "nexapay.payment.fraud_review_sla.overdue_cases",
                        overdueCases,
                        AtomicLong::get
                )
                .description("Open fraud review cases past SLA")
                .register(meterRegistry);

        Gauge.builder(
                        "nexapay.payment.fraud_review_sla.overdue_p1_cases",
                        overdueP1Cases,
                        AtomicLong::get
                )
                .description("Open P1 fraud review cases past SLA")
                .register(meterRegistry);

        registerPriorityGauge(FraudReviewPriority.P1, p1Cases);
        registerPriorityGauge(FraudReviewPriority.P2, p2Cases);
        registerPriorityGauge(FraudReviewPriority.P3, p3Cases);

        Gauge.builder(
                        "nexapay.payment.fraud_review_sla.oldest_open_age_seconds",
                        oldestOpenAgeSeconds,
                        AtomicLong::get
                )
                .description("Age in seconds of the oldest open fraud review case")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString =
                    "${nexapay.payment.fraud-review.sla-monitor.fixed-delay-ms:30000}",
            initialDelayString =
                    "${nexapay.payment.fraud-review.sla-monitor.initial-delay-ms:5000}"
    )
    @Transactional
    public void refresh() {
        OffsetDateTime now = OffsetDateTime.now();

        int escalated = repository.markOverdueAsEscalated(now);

        if (escalated > 0) {
            meterRegistry.counter(
                    "nexapay.payment.fraud_review_sla.escalated"
            ).increment(escalated);
        }

        openCases.set(
                repository.countByStatus(FraudReviewCaseStatus.OPEN)
        );

        overdueCases.set(
                repository.countOverdueOpenCases(now)
        );

        overdueP1Cases.set(
                repository.countOverdueP1Cases(now)
        );

        p1Cases.set(countPriority(FraudReviewPriority.P1));
        p2Cases.set(countPriority(FraudReviewPriority.P2));
        p3Cases.set(countPriority(FraudReviewPriority.P3));

        OffsetDateTime oldest = repository.findOldestOpenCaseAt();

        oldestOpenAgeSeconds.set(
                oldest == null
                        ? 0
                        : Math.max(
                                0,
                                Duration.between(oldest, now).toSeconds()
                        )
        );
    }

    private long countPriority(FraudReviewPriority priority) {
        return repository.countByStatusAndPriority(
                FraudReviewCaseStatus.OPEN,
                priority
        );
    }

    private void registerPriorityGauge(
            FraudReviewPriority priority,
            AtomicLong value) {
        Gauge.builder(
                        "nexapay.payment.fraud_review_sla.priority_cases",
                        value,
                        AtomicLong::get
                )
                .description("Open fraud review cases by priority")
                .tag("priority", priority.name())
                .register(meterRegistry);
    }
}
