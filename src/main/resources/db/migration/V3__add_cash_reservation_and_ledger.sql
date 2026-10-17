-- Funds reserved by open buy orders. available = balance - reserved.
ALTER TABLE cash_balances
    ADD COLUMN reserved NUMERIC(19, 4) NOT NULL DEFAULT 0.0000;

ALTER TABLE cash_balances
    ADD CONSTRAINT chk_cash_balances_reserved_non_negative CHECK (reserved >= 0),
    ADD CONSTRAINT chk_cash_balances_reserved_within_balance CHECK (reserved <= balance);

-- Append-only record of every cash movement. amount is always positive;
-- entry_type gives the direction. balance_after / reserved_after are snapshots after the entry.
CREATE TABLE cash_ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    balance_after NUMERIC(19, 4) NOT NULL,
    reserved_after NUMERIC(19, 4) NOT NULL,
    order_id BIGINT,
    trade_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cash_ledger_entries_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (id),

    CONSTRAINT chk_cash_ledger_entries_amount_positive CHECK (amount > 0),

    CONSTRAINT chk_cash_ledger_entries_entry_type
        CHECK (entry_type IN ('DEPOSIT', 'RESERVE', 'RELEASE', 'TRADE_DEBIT', 'TRADE_CREDIT'))
);

CREATE INDEX idx_cash_ledger_entries_account_id ON cash_ledger_entries (account_id, id DESC);
