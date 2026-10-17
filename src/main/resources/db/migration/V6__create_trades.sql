CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    quantity BIGINT NOT NULL,
    buy_order_id BIGINT NOT NULL,
    sell_order_id BIGINT NOT NULL,
    buyer_account_id BIGINT NOT NULL,
    seller_account_id BIGINT NOT NULL,
    -- Side of the incoming order that removed liquidity; the other side was the resting maker.
    aggressor_side VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_trades_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id),
    CONSTRAINT fk_trades_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders (id),
    CONSTRAINT fk_trades_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders (id),
    CONSTRAINT fk_trades_buyer_account FOREIGN KEY (buyer_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_trades_seller_account FOREIGN KEY (seller_account_id) REFERENCES accounts (id),
    CONSTRAINT chk_trades_price_positive CHECK (price > 0),
    CONSTRAINT chk_trades_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_trades_aggressor_side CHECK (aggressor_side IN ('BUY', 'SELL')),
    CONSTRAINT chk_trades_no_self_trade CHECK (buyer_account_id <> seller_account_id)
);

CREATE INDEX idx_trades_buyer_account_id ON trades (buyer_account_id, id DESC);
CREATE INDEX idx_trades_seller_account_id ON trades (seller_account_id, id DESC);
CREATE INDEX idx_trades_instrument_id ON trades (instrument_id, id DESC);

ALTER TABLE cash_ledger_entries
    ADD CONSTRAINT fk_cash_ledger_entries_trade FOREIGN KEY (trade_id) REFERENCES trades (id);
