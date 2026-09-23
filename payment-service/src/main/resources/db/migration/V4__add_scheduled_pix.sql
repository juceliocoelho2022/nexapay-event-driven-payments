ALTER TABLE payments
    ADD COLUMN scheduled_at TIMESTAMPTZ,
    ADD COLUMN executed_at TIMESTAMPTZ;

CREATE INDEX idx_payments_scheduled_due
    ON payments (scheduled_at)
    WHERE status = 'SCHEDULED';
