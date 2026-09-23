CREATE TABLE fraud_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    correlation_id VARCHAR(128),
    traceparent VARCHAR(255),
    tracestate TEXT,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_fraud_outbox_unpublished
    ON fraud_outbox_events (created_at)
    WHERE published = FALSE;
