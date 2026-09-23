package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.FraudReviewCase;
import br.com.nexapay.payment.domain.FraudReviewPriority;
import br.com.nexapay.payment.domain.FraudReviewCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface FraudReviewCaseRepository
        extends JpaRepository<FraudReviewCase, UUID> {

    @Query(value = """
            SELECT *
            FROM fraud_review_cases
            WHERE status = 'OPEN'
            ORDER BY
                CASE priority
                    WHEN 'P1' THEN 1
                    WHEN 'P2' THEN 2
                    ELSE 3
                END,
                sla_due_at ASC,
                opened_at ASC
            """, nativeQuery = true)
    List<FraudReviewCase> findOpenCasesOrdered();

    @Modifying
    @Query(value = """
            INSERT INTO fraud_review_cases (
                payment_id,
                status,
                opened_at,
                priority,
                sla_due_at
            ) VALUES (
                :paymentId,
                'OPEN',
                :openedAt,
                :priority,
                :slaDueAt
            )
            ON CONFLICT (payment_id) DO NOTHING
            """, nativeQuery = true)
    int insertOpenCaseIfAbsent(
            @Param("paymentId") UUID paymentId,
            @Param("openedAt") OffsetDateTime openedAt,
            @Param("priority") String priority,
            @Param("slaDueAt") OffsetDateTime slaDueAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE fraud_review_cases
            SET claimed_by = :reviewer,
                claimed_at = :now,
                claim_expires_at = :expiresAt
            WHERE payment_id = :paymentId
              AND status = 'OPEN'
              AND (
                    claimed_by IS NULL
                    OR claimed_by = :reviewer
                    OR claim_expires_at <= :now
              )
            """, nativeQuery = true)
    int claim(
            @Param("paymentId") UUID paymentId,
            @Param("reviewer") String reviewer,
            @Param("now") OffsetDateTime now,
            @Param("expiresAt") OffsetDateTime expiresAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE fraud_review_cases
            SET claimed_by = NULL,
                claimed_at = NULL,
                claim_expires_at = NULL
            WHERE payment_id = :paymentId
              AND status = 'OPEN'
              AND claimed_by = :reviewer
            """, nativeQuery = true)
    int release(
            @Param("paymentId") UUID paymentId,
            @Param("reviewer") String reviewer
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE fraud_review_cases
            SET status = 'RESOLVED',
                resolved_at = :resolvedAt,
                resolution = :resolution,
                claim_expires_at = NULL
            WHERE payment_id = :paymentId
              AND status = 'OPEN'
              AND claimed_by = :reviewer
              AND claim_expires_at > :resolvedAt
            """, nativeQuery = true)
    int resolveWithActiveLease(
            @Param("paymentId") UUID paymentId,
            @Param("reviewer") String reviewer,
            @Param("resolution") String resolution,
            @Param("resolvedAt") OffsetDateTime resolvedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE fraud_review_cases
            SET escalated_at = :now
            WHERE status = 'OPEN'
              AND sla_due_at <= :now
              AND escalated_at IS NULL
            """, nativeQuery = true)
    int markOverdueAsEscalated(@Param("now") OffsetDateTime now);

    long countByStatus(FraudReviewCaseStatus status);

    long countByStatusAndPriority(
            FraudReviewCaseStatus status,
            FraudReviewPriority priority
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM fraud_review_cases
            WHERE status = 'OPEN'
              AND sla_due_at <= :now
            """, nativeQuery = true)
    long countOverdueOpenCases(@Param("now") OffsetDateTime now);

    @Query(value = """
            SELECT COUNT(*)
            FROM fraud_review_cases
            WHERE status = 'OPEN'
              AND priority = 'P1'
              AND sla_due_at <= :now
            """, nativeQuery = true)
    long countOverdueP1Cases(@Param("now") OffsetDateTime now);

    @Query(value = """
            SELECT MIN(opened_at)
            FROM fraud_review_cases
            WHERE status = 'OPEN'
            """, nativeQuery = true)
    OffsetDateTime findOldestOpenCaseAt();
}
