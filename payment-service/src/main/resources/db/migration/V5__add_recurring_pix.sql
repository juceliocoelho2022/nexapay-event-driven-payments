CREATE TABLE recurring_pix_schedules (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    payer_account_id VARCHAR(80) NOT NULL,
    pix_key VARCHAR(180) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    description VARCHAR(255),
    frequency VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    next_occurrence_at TIMESTAMPTZ,
    remaining_occurrences INTEGER NOT NULL,
    anchor_day SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_recurring_pix_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_recurring_pix_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_recurring_pix_remaining_nonnegative CHECK (remaining_occurrences >= 0),
    CONSTRAINT ck_recurring_pix_anchor_day CHECK (anchor_day BETWEEN 1 AND 31)
);

CREATE INDEX idx_recurring_pix_due
    ON recurring_pix_schedules (next_occurrence_at)
    WHERE status = 'ACTIVE';

ALTER TABLE payments
    ADD COLUMN recurring_schedule_id UUID,
    ADD COLUMN recurring_occurrence_at TIMESTAMPTZ;

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_recurring_schedule
        FOREIGN KEY (recurring_schedule_id)
        REFERENCES recurring_pix_schedules (id);

CREATE UNIQUE INDEX uk_payments_recurring_occurrence
    ON payments (recurring_schedule_id, recurring_occurrence_at)
    WHERE recurring_schedule_id IS NOT NULL;
