ALTER TABLE payments
    ADD COLUMN fraud_decision VARCHAR(20),
    ADD COLUMN fraud_risk_score INTEGER,
    ADD COLUMN fraud_reason VARCHAR(500),
    ADD COLUMN fraud_decided_at TIMESTAMPTZ;

CREATE TABLE processed_fraud_decision_events (
    event_id UUID PRIMARY KEY,
    fraud_decision_id UUID NOT NULL UNIQUE,
    payment_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_processed_fraud_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_processed_fraud_payment
    ON processed_fraud_decision_events(payment_id);
