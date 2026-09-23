package br.com.nexapay.payment.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_review_cases")
public class FraudReviewCase {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FraudReviewCaseStatus status;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "claimed_by", length = 255)
    private String claimedBy;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Column(name = "claim_expires_at")
    private OffsetDateTime claimExpiresAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(length = 20)
    private String resolution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FraudReviewPriority priority;

    @Column(name = "sla_due_at", nullable = false)
    private OffsetDateTime slaDueAt;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    protected FraudReviewCase() {
    }

    public UUID getPaymentId() { return paymentId; }
    public FraudReviewCaseStatus getStatus() { return status; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public String getClaimedBy() { return claimedBy; }
    public OffsetDateTime getClaimedAt() { return claimedAt; }
    public OffsetDateTime getClaimExpiresAt() { return claimExpiresAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public String getResolution() { return resolution; }
    public FraudReviewPriority getPriority() { return priority; }
    public OffsetDateTime getSlaDueAt() { return slaDueAt; }
    public OffsetDateTime getEscalatedAt() { return escalatedAt; }
}
