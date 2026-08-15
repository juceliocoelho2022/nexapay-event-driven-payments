package br.com.nexapay.fraud.service;

import br.com.nexapay.fraud.domain.FraudDecision;
import br.com.nexapay.fraud.event.PaymentCreatedEvent;
import br.com.nexapay.fraud.repository.FraudDecisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class FraudService {

    private static final Logger log = LoggerFactory.getLogger(FraudService.class);

    private final FraudDecisionRepository repository;
    private final FraudRuleEngine ruleEngine;

    public FraudService(
            FraudDecisionRepository repository,
            FraudRuleEngine ruleEngine
    ) {
        this.repository = repository;
        this.ruleEngine = ruleEngine;
    }

    @Transactional
    public FraudDecision analyze(PaymentCreatedEvent event) {
        Optional<FraudDecision> existing = repository.findByEventId(event.eventId());

        if (existing.isPresent()) {
            log.info("Ignoring duplicated fraud event. eventId={}", event.eventId());
            return existing.get();
        }

        FraudRuleEngine.RiskAssessment assessment = ruleEngine.assess(event.amount());

        FraudDecision decision = FraudDecision.create(
                event.eventId(),
                event.paymentId(),
                event.payerAccountId(),
                event.pixKey(),
                event.amount(),
                assessment.decision(),
                assessment.riskScore(),
                assessment.reason(),
                event.occurredAt()
        );

        FraudDecision saved = repository.save(decision);

        log.info(
                "Fraud analysis completed. paymentId={}, decision={}, riskScore={}",
                saved.getPaymentId(),
                saved.getDecision(),
                saved.getRiskScore()
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<FraudDecision> findByPaymentId(UUID paymentId) {
        return repository.findByPaymentId(paymentId);
    }
}
