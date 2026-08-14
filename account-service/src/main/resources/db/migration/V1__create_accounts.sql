CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    account_number VARCHAR(30) NOT NULL UNIQUE,
    holder_name VARCHAR(120) NOT NULL,
    balance NUMERIC(19,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'))
);

CREATE INDEX idx_accounts_account_number ON accounts(account_number);
