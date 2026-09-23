package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.ProcessedFraudDecisionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ProcessedFraudDecisionEventRepository
        extends JpaRepository<ProcessedFraudDecisionEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_fraud_decision_events (
                event_id,
                fraud_decision_id,
                payment_id,
                processed_at
            ) VALUES (
                :eventId,
                :fraudDecisionId,
                :paymentId,
                :processedAt
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("fraudDecisionId") UUID fraudDecisionId,
            @Param("paymentId") UUID paymentId,
            @Param("processedAt") OffsetDateTime processedAt
    );
}
