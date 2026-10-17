package com.shuhongyi.trading.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shuhongyi.trading.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-0123456789";
    // The decoder validates expiry against the real clock, so issue relative to now.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JwtProperties properties = new JwtProperties(SECRET, "trading-risk-platform", Duration.ofHours(1));
    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtTokenService service = new JwtTokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issuedTokenHasUserIdSubjectIssuerAndOneHourExpiry() {
        JwtTokenService.IssuedToken token = service.issue(user(42L));

        Jwt jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("trading-risk-platform");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("user42@example.com");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String token = service.issue(user(1L)).value();
        SecretKey otherKey = new SecretKeySpec(
                "another-secret-that-is-also-long-enough-987654".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(otherKey).macAlgorithm(MacAlgorithm.HS256).build();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan32BytesIsRejectedAtStartup() {
        assertThatThrownBy(() -> new JwtProperties("too-short", "issuer", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    private static AppUser user(Long id) {
        return AppUser.builder().id(id).email("user" + id + "@example.com").build();
    }
}
