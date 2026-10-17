package com.shuhongyi.trading.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.user.AppUser;
import com.shuhongyi.trading.user.AppUserRepository;
import com.shuhongyi.trading.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class LoginApiIntegrationTest {

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String REGISTER_URL = "/api/auth/register";
    private static final String RAW_PASSWORD = "Password123!";
    private static final Set<String> FORBIDDEN_RESPONSE_FIELDS =
            Set.of("password", "passwordHash", "jwt", "token");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginWithValidCredentialsReturnsOk() throws Exception {
        String email = "alice@example.com";
        String fullName = "Alice Example";

        registerUser(email, RAW_PASSWORD, fullName);

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value(fullName))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNoSensitiveFieldsInResponse(responseJson);

        Long userId = appUserRepository.findByEmail(email).orElseThrow().getId();
        assertThat(responseJson.get("userId").asLong()).isEqualTo(userId);
    }

    @Test
    void loginWithUnknownEmailReturnsUnauthorized() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("unknown@example.com", RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        String email = "wrong-password@example.com";
        registerUser(email, RAW_PASSWORD, "Wrong Password User");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "WrongPassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginWithBlankEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("", RAW_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithBlankPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("blank-password-login@example.com", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginNormalizesEmailBeforeLookup() throws Exception {
        String email = "alice@example.com";
        registerUser(email, RAW_PASSWORD, "Alice Example");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("  ALICE@EXAMPLE.COM  ", RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void disabledUserLoginReturnsUnauthorized() throws Exception {
        String email = "disabled@example.com";

        appUserRepository.save(AppUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .fullName("Disabled User")
                .status(UserStatus.DISABLED)
                .build());

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, RAW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    private void registerUser(String email, String password, String fullName) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password, fullName)))
                .andExpect(status().isCreated());
    }

    private String loginJson(String email, String password) throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        return objectMapper.writeValueAsString(payload);
    }

    private String registerJson(String email, String password, String fullName) throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("fullName", fullName);
        return objectMapper.writeValueAsString(payload);
    }

    private void assertNoSensitiveFieldsInResponse(JsonNode responseJson) {
        responseJson.fieldNames().forEachRemaining(fieldName ->
                assertThat(FORBIDDEN_RESPONSE_FIELDS).doesNotContain(fieldName));
    }
}
