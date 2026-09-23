package br.com.nexapay.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "recurring_pix_schedules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recurring_pix_idempotency_key",
                columnNames = "idempotency_key"
        )
)
public class RecurringPixSchedule {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "payer_account_id", nullable = false, length = 80)
    private String payerAccountId;

    @Column(name = "pix_key", nullable = false, length = 180)
    private String pixKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurrenceFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecurringScheduleStatus status;

    @Column(name = "next_occurrence_at")
    private OffsetDateTime nextOccurrenceAt;

    @Column(name = "remaining_occurrences", nullable = false)
    private int remainingOccurrences;

    @Column(name = "anchor_day", nullable = false)
    private int anchorDay;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RecurringPixSchedule() {
    }

    public RecurringPixSchedule(
            UUID id,
            String idempotencyKey,
            String payerAccountId,
            String pixKey,
            BigDecimal amount,
            String description,
            RecurrenceFrequency frequency,
            RecurringScheduleStatus status,
            OffsetDateTime nextOccurrenceAt,
            int remainingOccurrences,
            int anchorDay,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.payerAccountId = payerAccountId;
        this.pixKey = pixKey;
        this.amount = amount;
        this.description = description;
        this.frequency = frequency;
        this.status = status;
        this.nextOccurrenceAt = nextOccurrenceAt;
        this.remainingOccurrences = remainingOccurrences;
        this.anchorDay = anchorDay;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayerAccountId() { return payerAccountId; }
    public String getPixKey() { return pixKey; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public RecurrenceFrequency getFrequency() { return frequency; }
    public RecurringScheduleStatus getStatus() { return status; }
    public OffsetDateTime getNextOccurrenceAt() { return nextOccurrenceAt; }
    public int getRemainingOccurrences() { return remainingOccurrences; }
    public int getAnchorDay() { return anchorDay; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
