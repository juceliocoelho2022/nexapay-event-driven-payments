package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
}
