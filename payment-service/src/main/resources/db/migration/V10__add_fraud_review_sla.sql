ALTER TABLE fraud_review_cases
    ADD COLUMN priority VARCHAR(10),
    ADD COLUMN sla_due_at TIMESTAMPTZ,
    ADD COLUMN escalated_at TIMESTAMPTZ;

UPDATE fraud_review_cases frc
SET priority = CASE
        WHEN COALESCE(p.fraud_risk_score, 0) >= 85 OR p.amount >= 9000.00 THEN 'P1'
        WHEN COALESCE(p.fraud_risk_score, 0) >= 80 OR p.amount >= 7500.00 THEN 'P2'
        ELSE 'P3'
    END,
    sla_due_at = frc.opened_at + CASE
        WHEN COALESCE(p.fraud_risk_score, 0) >= 85 OR p.amount >= 9000.00 THEN INTERVAL '15 minutes'
        WHEN COALESCE(p.fraud_risk_score, 0) >= 80 OR p.amount >= 7500.00 THEN INTERVAL '30 minutes'
        ELSE INTERVAL '60 minutes'
    END
FROM payments p
WHERE p.id = frc.payment_id;

ALTER TABLE fraud_review_cases
    ALTER COLUMN priority SET NOT NULL,
    ALTER COLUMN sla_due_at SET NOT NULL;

ALTER TABLE fraud_review_cases
    ADD CONSTRAINT ck_fraud_review_case_priority
        CHECK (priority IN ('P1', 'P2', 'P3'));

CREATE INDEX idx_fraud_review_cases_priority_sla
    ON fraud_review_cases(priority, sla_due_at, opened_at)
    WHERE status = 'OPEN';

CREATE INDEX idx_fraud_review_cases_overdue
    ON fraud_review_cases(sla_due_at)
    WHERE status = 'OPEN';
