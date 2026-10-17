package com.shuhongyi.trading.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings bound from {@code app.security.jwt.*}.
 * The secret comes from the {@code JWT_SECRET} environment variable; only the dev profile has a default.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least %d bytes for HS256; set the JWT_SECRET environment variable"
                            .formatted(MIN_SECRET_BYTES));
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "trading-risk-platform";
        }
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
