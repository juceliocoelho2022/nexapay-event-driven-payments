CREATE TABLE fraud_review_cases (
    payment_id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    claimed_by VARCHAR(255),
    claimed_at TIMESTAMPTZ,
    claim_expires_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    resolution VARCHAR(20),

    CONSTRAINT fk_fraud_review_case_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id),

    CONSTRAINT ck_fraud_review_case_status
        CHECK (status IN ('OPEN', 'RESOLVED')),

    CONSTRAINT ck_fraud_review_case_resolution
        CHECK (
            resolution IS NULL
            OR resolution IN ('APPROVE', 'REJECT')
        )
);

CREATE INDEX idx_fraud_review_cases_opened
    ON fraud_review_cases(opened_at)
    WHERE status = 'OPEN';

CREATE INDEX idx_fraud_review_cases_claim_expiry
    ON fraud_review_cases(claim_expires_at)
    WHERE status = 'OPEN';

INSERT INTO fraud_review_cases (
    payment_id,
    status,
    opened_at
)
SELECT
    id,
    'OPEN',
    COALESCE(fraud_decided_at, created_at)
FROM payments
WHERE status = 'REVIEW'
ON CONFLICT (payment_id) DO NOTHING;
