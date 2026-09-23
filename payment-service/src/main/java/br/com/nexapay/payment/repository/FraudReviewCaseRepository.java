package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.FraudReviewCase;
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

    List<FraudReviewCase> findByStatusOrderByOpenedAtAsc(
            FraudReviewCaseStatus status
    );

    @Modifying
    @Query(value = """
            INSERT INTO fraud_review_cases (
                payment_id,
                status,
                opened_at
            ) VALUES (
                :paymentId,
                'OPEN',
                :openedAt
            )
            ON CONFLICT (payment_id) DO NOTHING
            """, nativeQuery = true)
    int insertOpenCaseIfAbsent(
            @Param("paymentId") UUID paymentId,
            @Param("openedAt") OffsetDateTime openedAt
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
}
