CREATE TABLE instruments (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    -- Simulated reference price used for price-band checks until the first trade.
    reference_price NUMERIC(19, 4) NOT NULL,
    last_trade_price NUMERIC(19, 4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_instruments_symbol UNIQUE (symbol),
    CONSTRAINT chk_instruments_status CHECK (status IN ('ACTIVE', 'HALTED')),
    CONSTRAINT chk_instruments_reference_price_positive CHECK (reference_price > 0),
    CONSTRAINT chk_instruments_last_trade_price_positive CHECK (last_trade_price IS NULL OR last_trade_price > 0)
);

-- Simulated reference prices for demonstration only; they are not market data.
INSERT INTO instruments (symbol, name, reference_price) VALUES
    ('AAPL', 'Apple Inc.', 190.0000),
    ('MSFT', 'Microsoft Corporation', 420.0000),
    ('NVDA', 'NVIDIA Corporation', 120.0000),
    ('TSLA', 'Tesla, Inc.', 250.0000),
    ('AMZN', 'Amazon.com, Inc.', 180.0000);

-- Shares held per account and instrument. reserved_quantity is earmarked for open sell orders.
-- Short selling is not allowed, so both columns are non-negative and reserved never exceeds quantity.
CREATE TABLE positions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 0,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_positions_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_positions_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id),
    CONSTRAINT uk_positions_account_instrument UNIQUE (account_id, instrument_id),
    CONSTRAINT chk_positions_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT chk_positions_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_positions_reserved_within_quantity CHECK (reserved_quantity <= quantity)
);

CREATE INDEX idx_positions_instrument_id ON positions (instrument_id);
