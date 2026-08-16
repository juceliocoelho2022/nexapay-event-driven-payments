package br.com.nexapay.fraud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "fraud_decisions")
public class FraudDecision {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "payer_account_id", nullable = false, length = 100)
    private String payerAccountId;

    @Column(name = "pix_key", nullable = false, length = 255)
    private String pixKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FraudDecisionType decision;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "analyzed_at", nullable = false)
    private OffsetDateTime analyzedAt;

    protected FraudDecision() {
    }

    private FraudDecision(
            UUID id,
            UUID eventId,
            UUID paymentId,
            String payerAccountId,
            String pixKey,
            BigDecimal amount,
            FraudDecisionType decision,
            int riskScore,
            String reason,
            OffsetDateTime occurredAt,
            OffsetDateTime analyzedAt
    ) {
        this.id = id;
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.payerAccountId = payerAccountId;
        this.pixKey = pixKey;
        this.amount = amount;
        this.decision = decision;
        this.riskScore = riskScore;
        this.reason = reason;
        this.occurredAt = occurredAt;
        this.analyzedAt = analyzedAt;
    }

    public static FraudDecision create(
            UUID eventId,
            UUID paymentId,
            String payerAccountId,
            String pixKey,
            BigDecimal amount,
            FraudDecisionType decision,
            int riskScore,
            String reason,
            OffsetDateTime occurredAt
    ) {
        return new FraudDecision(
                UUID.randomUUID(),
                eventId,
                paymentId,
                payerAccountId,
                pixKey,
                amount,
                decision,
                riskScore,
                reason,
                occurredAt,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getPayerAccountId() {
        return payerAccountId;
    }

    public String getPixKey() {
        return pixKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public FraudDecisionType getDecision() {
        return decision;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getAnalyzedAt() {
        return analyzedAt;
    }
}
