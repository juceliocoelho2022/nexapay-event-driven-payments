package br.com.nexapay.payment.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_fraud_decision_events")
public class ProcessedFraudDecisionEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "fraud_decision_id", nullable = false, unique = true)
    private UUID fraudDecisionId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedFraudDecisionEvent() {
    }

    public UUID getEventId() { return eventId; }
    public UUID getFraudDecisionId() { return fraudDecisionId; }
    public UUID getPaymentId() { return paymentId; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
}
