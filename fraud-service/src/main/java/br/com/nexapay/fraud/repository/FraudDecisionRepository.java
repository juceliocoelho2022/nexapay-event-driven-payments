package br.com.nexapay.fraud.repository;

import br.com.nexapay.fraud.domain.FraudDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface FraudDecisionRepository extends JpaRepository<FraudDecision, UUID> {

    Optional<FraudDecision> findByEventId(UUID eventId);

    Optional<FraudDecision> findByPaymentId(UUID paymentId);

    @Modifying
    @Query(value = """
            INSERT INTO fraud_decisions (
                id, event_id, payment_id, payer_account_id, pix_key,
                amount, decision, risk_score, reason, occurred_at, analyzed_at
            ) VALUES (
                :id, :eventId, :paymentId, :payerAccountId, :pixKey,
                :amount, :decision, :riskScore, :reason, :occurredAt, :analyzedAt
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfEventAbsent(
            @Param("id") UUID id,
            @Param("eventId") UUID eventId,
            @Param("paymentId") UUID paymentId,
            @Param("payerAccountId") String payerAccountId,
            @Param("pixKey") String pixKey,
            @Param("amount") BigDecimal amount,
            @Param("decision") String decision,
            @Param("riskScore") int riskScore,
            @Param("reason") String reason,
            @Param("occurredAt") OffsetDateTime occurredAt,
            @Param("analyzedAt") OffsetDateTime analyzedAt
    );
}
