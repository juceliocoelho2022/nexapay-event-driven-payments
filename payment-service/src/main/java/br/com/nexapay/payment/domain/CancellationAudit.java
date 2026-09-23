package br.com.nexapay.payment.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cancellation_audit")
public class CancellationAudit {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private CancellationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "actor_subject", nullable = false, length = 255)
    private String actorSubject;

    @Column(name = "cancelled_at", nullable = false)
    private OffsetDateTime cancelledAt;

    @Column(name = "affected_scheduled_payments", nullable = false)
    private int affectedScheduledPayments;

    protected CancellationAudit() {
    }

    public CancellationAudit(
            UUID id,
            CancellationTargetType targetType,
            UUID targetId,
            String reason,
            String actorSubject,
            OffsetDateTime cancelledAt,
            int affectedScheduledPayments) {
        this.id = id;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.actorSubject = actorSubject;
        this.cancelledAt = cancelledAt;
        this.affectedScheduledPayments = affectedScheduledPayments;
    }

    public UUID getId() { return id; }
    public CancellationTargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getActorSubject() { return actorSubject; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public int getAffectedScheduledPayments() { return affectedScheduledPayments; }
}
