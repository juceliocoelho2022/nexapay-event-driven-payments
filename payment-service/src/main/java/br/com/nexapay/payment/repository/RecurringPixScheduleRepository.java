package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.RecurringPixSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringPixScheduleRepository extends JpaRepository<RecurringPixSchedule, UUID> {

    Optional<RecurringPixSchedule> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
            INSERT INTO recurring_pix_schedules (
                id,
                idempotency_key,
                payer_account_id,
                pix_key,
                amount,
                description,
                frequency,
                status,
                next_occurrence_at,
                remaining_occurrences,
                anchor_day,
                created_at,
                updated_at
            ) VALUES (
                :id,
                :idempotencyKey,
                :payerAccountId,
                :pixKey,
                :amount,
                :description,
                :frequency,
                'ACTIVE',
                :firstOccurrenceAt,
                :occurrences,
                :anchorDay,
                :now,
                :now
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
            @Param("frequency") String frequency,
            @Param("firstOccurrenceAt") OffsetDateTime firstOccurrenceAt,
            @Param("occurrences") int occurrences,
            @Param("anchorDay") int anchorDay,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
            SELECT id
            FROM recurring_pix_schedules
            WHERE status = 'ACTIVE'
              AND next_occurrence_at <= :now
            ORDER BY next_occurrence_at ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findDueScheduleIds(
            @Param("now") OffsetDateTime now,
            @Param("batchSize") int batchSize
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE recurring_pix_schedules
            SET next_occurrence_at = :nextOccurrenceAt,
                remaining_occurrences = :remainingOccurrences,
                updated_at = :now
            WHERE id = :id
              AND status = 'ACTIVE'
              AND next_occurrence_at = :expectedOccurrenceAt
              AND next_occurrence_at <= :now
            """, nativeQuery = true)
    int advanceOccurrence(
            @Param("id") UUID id,
            @Param("expectedOccurrenceAt") OffsetDateTime expectedOccurrenceAt,
            @Param("nextOccurrenceAt") OffsetDateTime nextOccurrenceAt,
            @Param("remainingOccurrences") int remainingOccurrences,
            @Param("now") OffsetDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE recurring_pix_schedules
            SET status = 'CANCELLED',
                next_occurrence_at = NULL,
                cancelled_at = :cancelledAt,
                cancellation_reason = :reason,
                updated_at = :cancelledAt
            WHERE id = :id
              AND status = 'ACTIVE'
            """, nativeQuery = true)
    int cancelActiveSchedule(
            @Param("id") UUID id,
            @Param("reason") String reason,
            @Param("cancelledAt") OffsetDateTime cancelledAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE recurring_pix_schedules
            SET next_occurrence_at = NULL,
                remaining_occurrences = 0,
                status = 'COMPLETED',
                updated_at = :now
            WHERE id = :id
              AND status = 'ACTIVE'
              AND next_occurrence_at = :expectedOccurrenceAt
              AND next_occurrence_at <= :now
            """, nativeQuery = true)
    int completeOccurrence(
            @Param("id") UUID id,
            @Param("expectedOccurrenceAt") OffsetDateTime expectedOccurrenceAt,
            @Param("now") OffsetDateTime now
    );
}
