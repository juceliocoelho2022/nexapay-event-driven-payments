package br.com.nexapay.fraud.repository;

import br.com.nexapay.fraud.domain.FraudOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudOutboxEventRepository
        extends JpaRepository<FraudOutboxEvent, UUID> {

    List<FraudOutboxEvent> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
