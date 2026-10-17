package com.shuhongyi.trading.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.auth.JwtProperties;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.support.TestUser;
import com.shuhongyi.trading.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MeApiIntegrationTest {

    private static final String ME_URL = "/api/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void meReturnsAuthenticatedUserAndAccount() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.userId()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountId").value(user.accountId()))
                .andExpect(jsonPath("$.accountNumber").value(startsWith("ACC-")))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void missingTokenReturnsUnauthorizedProblem() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void tamperedTokenReturnsUnauthorized() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        String token = user.accessToken();
        // Keep header and claims but scramble the signature segment.
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        Instant issuedAt = Instant.now().minus(Duration.ofHours(2));
        String expired = encode(user.userId(), jwtProperties.issuer(), issuedAt, issuedAt.plus(Duration.ofHours(1)));

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerReturnsUnauthorized() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        Instant now = Instant.now();
        String foreign = encode(user.userId(), "someone-else", now, now.plus(Duration.ofHours(1)));

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPathsRequireAuthenticationEvenWhenUnmapped() throws Exception {
        mockMvc.perform(get("/api/anything-else"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private String encode(Long userId, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
