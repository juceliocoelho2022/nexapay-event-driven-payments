package br.com.nexapay.payment.repository;

import br.com.nexapay.payment.domain.CancellationAudit;
import br.com.nexapay.payment.domain.CancellationTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CancellationAuditRepository extends JpaRepository<CancellationAudit, UUID> {

    List<CancellationAudit> findByTargetTypeAndTargetIdOrderByCancelledAtDesc(
            CancellationTargetType targetType,
            UUID targetId
    );
}
