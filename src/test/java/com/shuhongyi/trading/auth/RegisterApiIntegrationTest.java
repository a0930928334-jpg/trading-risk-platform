package com.shuhongyi.trading.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.account.Account;
import com.shuhongyi.trading.account.AccountRepository;
import com.shuhongyi.trading.account.AccountStatus;
import com.shuhongyi.trading.account.CashBalance;
import com.shuhongyi.trading.account.CashBalanceRepository;
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

import java.math.BigDecimal;
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
class RegisterApiIntegrationTest {

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
    private AccountRepository accountRepository;

    @Autowired
    private CashBalanceRepository cashBalanceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldRegisterUserAndCreateAccountAndCashBalance() throws Exception {
        String normalizedEmail = "register-success@example.com";
        String trimmedFullName = "Alice Example";

        MvcResult result = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(
                                "  Register-Success@Example.COM  ",
                                RAW_PASSWORD,
                                "  " + trimmedFullName + "  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(normalizedEmail))
                .andExpect(jsonPath("$.fullName").value(trimmedFullName))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNoSensitiveFieldsInResponse(responseJson);

        String accountNumber = responseJson.get("accountNumber").asText();
        assertThat(accountNumber).startsWith("ACC-");

        Long accountId = responseJson.get("accountId").asLong();

        AppUser user = appUserRepository.findByEmail(normalizedEmail).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getFullName()).isEqualTo(trimmedFullName);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);

        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCurrency()).isEqualTo("USD");
        assertThat(account.getUser().getId()).isEqualTo(user.getId());

        CashBalance cashBalance = cashBalanceRepository.findByAccount_Id(accountId).orElseThrow();
        assertThat(cashBalance.getCurrency()).isEqualTo("USD");
        assertThat(cashBalance.getBalance()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        String email = "duplicate-email@example.com";
        String requestBody = registerJson(email, RAW_PASSWORD, "Duplicate User");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered: " + email));
    }

    @Test
    void shouldRejectBlankEmail() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("", RAW_PASSWORD, "Blank Email User")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectBlankPassword() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("blank-password@example.com", "", "Blank Password User")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(
                                "blank-password-whitespace@example.com",
                                "   ",
                                "Blank Password Whitespace User")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectBlankFullName() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("blank-fullname@example.com", RAW_PASSWORD, "")))
                .andExpect(status().isBadRequest());
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
