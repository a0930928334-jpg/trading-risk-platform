CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_app_users_email UNIQUE (email)
);

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_number VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES app_users (id),

    CONSTRAINT uk_accounts_account_number UNIQUE (account_number)
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

CREATE TABLE cash_balances (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_cash_balances_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (id),

    CONSTRAINT uk_cash_balances_account_id UNIQUE (account_id),

    CONSTRAINT chk_cash_balances_balance_non_negative
        CHECK (balance >= 0)
);
