package br.com.nexapay.payment.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "manual_fraud_review_audit")
public class ManualFraudReviewAudit {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ManualFraudReviewDecision decision;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "reviewer_subject", nullable = false, length = 255)
    private String reviewerSubject;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 30)
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_status", nullable = false, length = 30)
    private PaymentStatus finalStatus;

    protected ManualFraudReviewAudit() {
    }

    public ManualFraudReviewAudit(
            UUID id,
            UUID paymentId,
            ManualFraudReviewDecision decision,
            String reason,
            String reviewerSubject,
            OffsetDateTime reviewedAt,
            PaymentStatus previousStatus,
            PaymentStatus finalStatus) {
        this.id = id;
        this.paymentId = paymentId;
        this.decision = decision;
        this.reason = reason;
        this.reviewerSubject = reviewerSubject;
        this.reviewedAt = reviewedAt;
        this.previousStatus = previousStatus;
        this.finalStatus = finalStatus;
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public ManualFraudReviewDecision getDecision() { return decision; }
    public String getReason() { return reason; }
    public String getReviewerSubject() { return reviewerSubject; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public PaymentStatus getPreviousStatus() { return previousStatus; }
    public PaymentStatus getFinalStatus() { return finalStatus; }
}
