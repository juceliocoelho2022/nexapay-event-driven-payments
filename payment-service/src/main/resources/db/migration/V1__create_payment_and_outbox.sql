CREATE TABLE payments (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    payer_account_id VARCHAR(80) NOT NULL,
    pix_key VARCHAR(180) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (published, created_at);
