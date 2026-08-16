CREATE TABLE fraud_decisions (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    payment_id UUID NOT NULL,
    payer_account_id VARCHAR(100) NOT NULL,
    pix_key VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    risk_score INTEGER NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_fraud_decision CHECK (decision IN ('APPROVED', 'REVIEW', 'BLOCKED')),
    CONSTRAINT chk_fraud_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_fraud_risk_score CHECK (risk_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_fraud_decisions_payment_id
    ON fraud_decisions(payment_id);

CREATE INDEX idx_fraud_decisions_payer_account_id
    ON fraud_decisions(payer_account_id);

CREATE INDEX idx_fraud_decisions_analyzed_at
    ON fraud_decisions(analyzed_at);
