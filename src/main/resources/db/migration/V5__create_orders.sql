CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    side VARCHAR(8) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    time_in_force VARCHAR(8) NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    quantity BIGINT NOT NULL,
    filled_quantity BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    reject_code VARCHAR(64),
    reject_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_orders_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id),
    CONSTRAINT chk_orders_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_orders_type CHECK (order_type IN ('LIMIT')),
    CONSTRAINT chk_orders_time_in_force CHECK (time_in_force IN ('GTC')),
    CONSTRAINT chk_orders_status
        CHECK (status IN ('NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED')),
    CONSTRAINT chk_orders_price_positive CHECK (price > 0),
    CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_orders_filled_within_quantity CHECK (filled_quantity >= 0 AND filled_quantity <= quantity)
);

CREATE INDEX idx_orders_account_id ON orders (account_id, id DESC);

-- Open orders are reloaded into the in-memory order books at startup.
CREATE INDEX idx_orders_open_by_instrument ON orders (instrument_id, id)
    WHERE status IN ('NEW', 'PARTIALLY_FILLED');

ALTER TABLE cash_ledger_entries
    ADD CONSTRAINT fk_cash_ledger_entries_order FOREIGN KEY (order_id) REFERENCES orders (id);
