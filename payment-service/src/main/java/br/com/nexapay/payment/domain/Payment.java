package br.com.nexapay.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "payments",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_payments_idempotency_key",
        columnNames = "idempotency_key"
    )
)
public class Payment {

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
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "recurring_schedule_id")
    private UUID recurringScheduleId;

    @Column(name = "recurring_occurrence_at")
    private OffsetDateTime recurringOccurrenceAt;

    protected Payment() {
    }

    public Payment(
            UUID id,
            String idempotencyKey,
            String payerAccountId,
            String pixKey,
            BigDecimal amount,
            String description,
            PaymentStatus status,
            OffsetDateTime createdAt) {
        this(
                id,
                idempotencyKey,
                payerAccountId,
                pixKey,
                amount,
                description,
                status,
                createdAt,
                null,
                null,
                null,
                null
        );
    }

    public Payment(
            UUID id,
            String idempotencyKey,
            String payerAccountId,
            String pixKey,
            BigDecimal amount,
            String description,
            PaymentStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime scheduledAt,
            OffsetDateTime executedAt) {
        this(
                id,
                idempotencyKey,
                payerAccountId,
                pixKey,
                amount,
                description,
                status,
                createdAt,
                scheduledAt,
                executedAt,
                null,
                null
        );
    }

    public Payment(
            UUID id,
            String idempotencyKey,
            String payerAccountId,
            String pixKey,
            BigDecimal amount,
            String description,
            PaymentStatus status,
            OffsetDateTime createdAt,
            OffsetDateTime scheduledAt,
            OffsetDateTime executedAt,
            UUID recurringScheduleId,
            OffsetDateTime recurringOccurrenceAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.payerAccountId = payerAccountId;
        this.pixKey = pixKey;
        this.amount = amount;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.scheduledAt = scheduledAt;
        this.executedAt = executedAt;
        this.recurringScheduleId = recurringScheduleId;
        this.recurringOccurrenceAt = recurringOccurrenceAt;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPayerAccountId() { return payerAccountId; }
    public String getPixKey() { return pixKey; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public PaymentStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public UUID getRecurringScheduleId() { return recurringScheduleId; }
    public OffsetDateTime getRecurringOccurrenceAt() { return recurringOccurrenceAt; }
}
