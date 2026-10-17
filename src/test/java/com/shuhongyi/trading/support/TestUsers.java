package com.shuhongyi.trading.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registers and logs in users through the public API so tests exercise the real authentication flow.
 */
public final class TestUsers {

    public static final String PASSWORD = "Password123!";

    private TestUsers() {
    }

    public static TestUser create(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        return create(mockMvc, objectMapper, "user-" + UUID.randomUUID() + "@example.com");
    }

    public static TestUser create(MockMvc mockMvc, ObjectMapper objectMapper, String email) throws Exception {
        String registerBody = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", PASSWORD,
                "fullName", "Test User"));
        JsonNode registered = objectMapper.readTree(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD));
        JsonNode loggedIn = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        return new TestUser(
                registered.get("userId").asLong(),
                registered.get("accountId").asLong(),
                email,
                loggedIn.get("accessToken").asText());
    }
}
