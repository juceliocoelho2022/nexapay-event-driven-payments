package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO payments (
                id,
                idempotency_key,
                payer_account_id,
                pix_key,
                amount,
                description,
                status,
                created_at
            ) VALUES (
                :id,
                :idempotencyKey,
                :payerAccountId,
                :pixKey,
                :amount,
                :description,
                :status,
                :createdAt
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfIdempotencyKeyAbsent(
            @Param("id") UUID id,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("payerAccountId") String payerAccountId,
            @Param("pixKey") String pixKey,
            @Param("amount") BigDecimal amount,
            @Param("description") String description,
            @Param("status") String status,
            @Param("createdAt") OffsetDateTime createdAt
    );

    @Modifying
    @Query(value = """
            INSERT INTO payments (
                id,
                idempotency_key,
                payer_account_id,
                pix_key,
                amount,
                description,
                status,
                created_at,
                scheduled_at
            ) VALUES (
                :id,
                :idempotencyKey,
                :payerAccountId,
                :pixKey,
                :amount,
                :description,
                'SCHEDULED',
                :createdAt,
                :scheduledAt
            )
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertScheduledIfIdempotencyKeyAbsent(
            @Param("id") UUID id,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("payerAccountId") String payerAccountId,
            @Param("pixKey") String pixKey,
            @Param("amount") BigDecimal amount,
            @Param("description") String description,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("scheduledAt") OffsetDateTime scheduledAt
    );

    @Modifying
    @Query(value = """
            INSERT INTO payments (
                id,
                idempotency_key,
                payer_account_id,
                pix_key,
                amount,
                description,
                status,
                created_at,
                scheduled_at,
                recurring_schedule_id,
                recurring_occurrence_at
            ) VALUES (
                :id,
                :idempotencyKey,
                :payerAccountId,
                :pixKey,
                :amount,
                :description,
                'SCHEDULED',
                :createdAt,
                :scheduledAt,
                :recurringScheduleId,
                :recurringOccurrenceAt
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertRecurringScheduledIfAbsent(
            @Param("id") UUID id,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("payerAccountId") String payerAccountId,
            @Param("pixKey") String pixKey,
            @Param("amount") BigDecimal amount,
            @Param("description") String description,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("scheduledAt") OffsetDateTime scheduledAt,
            @Param("recurringScheduleId") UUID recurringScheduleId,
            @Param("recurringOccurrenceAt") OffsetDateTime recurringOccurrenceAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payments
            SET status = 'CANCELLED',
                cancelled_at = :cancelledAt,
                cancellation_reason = :reason
            WHERE id = :id
              AND status = 'SCHEDULED'
            """, nativeQuery = true)
    int cancelScheduledPayment(
            @Param("id") UUID id,
            @Param("reason") String reason,
            @Param("cancelledAt") OffsetDateTime cancelledAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payments
            SET status = 'CANCELLED',
                cancelled_at = :cancelledAt,
                cancellation_reason = :reason
            WHERE recurring_schedule_id = :scheduleId
              AND status = 'SCHEDULED'
            """, nativeQuery = true)
    int cancelScheduledPaymentsForRecurringSchedule(
            @Param("scheduleId") UUID scheduleId,
            @Param("reason") String reason,
            @Param("cancelledAt") OffsetDateTime cancelledAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payments
            SET status = :targetStatus,
                fraud_decision = :decision,
                fraud_risk_score = :riskScore,
                fraud_reason = :reason,
                fraud_decided_at = :decidedAt
            WHERE id = :paymentId
              AND status = 'PENDING'
            """, nativeQuery = true)
    int applyFraudDecision(
            @Param("paymentId") UUID paymentId,
            @Param("targetStatus") String targetStatus,
            @Param("decision") String decision,
            @Param("riskScore") int riskScore,
            @Param("reason") String reason,
            @Param("decidedAt") OffsetDateTime decidedAt
    );

    @Query(value = """
            SELECT id
            FROM payments
            WHERE status = 'SCHEDULED'
              AND scheduled_at <= :now
            ORDER BY scheduled_at ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findDueScheduledPaymentIds(
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payments
            SET status = 'PENDING',
                executed_at = :executedAt
            WHERE id = :id
              AND status = 'SCHEDULED'
              AND scheduled_at <= :executedAt
            """, nativeQuery = true)
    int claimScheduledPaymentForExecution(
            @Param("id") UUID id,
            @Param("executedAt") OffsetDateTime executedAt
    );
}
