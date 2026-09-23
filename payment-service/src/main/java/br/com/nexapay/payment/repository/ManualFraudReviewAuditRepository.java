package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.ManualFraudReviewAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ManualFraudReviewAuditRepository
        extends JpaRepository<ManualFraudReviewAudit, UUID> {

    List<ManualFraudReviewAudit> findByPaymentIdOrderByReviewedAtDesc(UUID paymentId);
}
