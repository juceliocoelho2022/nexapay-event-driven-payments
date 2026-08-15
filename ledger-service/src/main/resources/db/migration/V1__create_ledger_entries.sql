CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    account_id UUID NOT NULL,
    account_number VARCHAR(30) NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    balance_after NUMERIC(19,2) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_ledger_entry_type
        CHECK (entry_type IN ('CREDIT', 'DEBIT')),

    CONSTRAINT chk_ledger_amount_positive
        CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entries_account_id
    ON ledger_entries(account_id);

CREATE INDEX idx_ledger_entries_occurred_at
    ON ledger_entries(occurred_at);
