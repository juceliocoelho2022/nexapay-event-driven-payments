ALTER TABLE payments
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancellation_reason VARCHAR(255);

ALTER TABLE recurring_pix_schedules
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancellation_reason VARCHAR(255);

CREATE TABLE cancellation_audit (
    id UUID PRIMARY KEY,
    target_type VARCHAR(30) NOT NULL,
    target_id UUID NOT NULL,
    reason VARCHAR(255) NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    cancelled_at TIMESTAMPTZ NOT NULL,
    affected_scheduled_payments INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT ck_cancellation_affected_nonnegative
        CHECK (affected_scheduled_payments >= 0)
);

CREATE INDEX idx_cancellation_audit_target
    ON cancellation_audit (target_type, target_id, cancelled_at);
