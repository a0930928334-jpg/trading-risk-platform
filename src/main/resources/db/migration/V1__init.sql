CREATE TABLE IF NOT EXISTS app_health_check (
                                                id BIGSERIAL PRIMARY KEY,
                                                status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );