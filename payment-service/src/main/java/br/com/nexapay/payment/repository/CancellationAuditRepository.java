package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.CancellationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CancellationAuditRepository extends JpaRepository<CancellationAudit, UUID> {
}
