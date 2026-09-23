ALTER TABLE payments
    ADD COLUMN manual_review_decision VARCHAR(20),
    ADD COLUMN manual_review_reason VARCHAR(500),
    ADD COLUMN manual_reviewer_subject VARCHAR(255),
    ADD COLUMN manual_reviewed_at TIMESTAMPTZ;

CREATE TABLE manual_fraud_review_audit (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    decision VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    reviewer_subject VARCHAR(255) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    previous_status VARCHAR(30) NOT NULL,
    final_status VARCHAR(30) NOT NULL,

    CONSTRAINT fk_manual_fraud_review_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id),

    CONSTRAINT ck_manual_fraud_review_decision
        CHECK (decision IN ('APPROVE', 'REJECT')),

    CONSTRAINT ck_manual_fraud_review_previous_status
        CHECK (previous_status = 'REVIEW'),

    CONSTRAINT ck_manual_fraud_review_final_status
        CHECK (final_status IN ('COMPLETED', 'REJECTED'))
);

CREATE INDEX idx_manual_fraud_review_payment
    ON manual_fraud_review_audit(payment_id, reviewed_at DESC);
