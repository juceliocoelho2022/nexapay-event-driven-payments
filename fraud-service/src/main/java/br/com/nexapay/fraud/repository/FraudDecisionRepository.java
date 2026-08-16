package br.com.nexapay.fraud.repository;

import br.com.nexapay.fraud.domain.FraudDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FraudDecisionRepository extends JpaRepository<FraudDecision, UUID> {

    Optional<FraudDecision> findByEventId(UUID eventId);

    Optional<FraudDecision> findByPaymentId(UUID paymentId);
}
