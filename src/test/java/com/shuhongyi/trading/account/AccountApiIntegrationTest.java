package com.shuhongyi.trading.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.support.TestUser;
import com.shuhongyi.trading.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void newAccountStartsWithZeroBalance() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        mockMvc.perform(get("/api/account").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(user.accountId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void depositIncreasesBalanceAndWritesLedgerEntry() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        deposit(user, "1500.2500")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entry.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.entry.amount").value(1500.25))
                .andExpect(jsonPath("$.entry.balanceAfter").value(1500.25))
                .andExpect(jsonPath("$.entry.reservedAfter").value(0))
                .andExpect(jsonPath("$.account.balance").value(1500.25))
                .andExpect(jsonPath("$.account.available").value(1500.25));

        mockMvc.perform(get("/api/account").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$.balance").value(1500.25));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10", "0.00001", "1000000.0001"})
    void invalidDepositAmountsAreRejected(String amount) throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        deposit(user, amount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void missingDepositAmountIsRejected() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        mockMvc.perform(post("/api/account/deposits")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ledgerIsPaginatedNewestFirst() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);
        deposit(user, "1.0000");
        deposit(user, "2.0000");
        deposit(user, "3.0000");

        mockMvc.perform(get("/api/account/ledger")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(3))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(6))
                .andExpect(jsonPath("$.content[1].amount").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void ledgerPageSizeIsCappedAtOneHundred() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        mockMvc.perform(get("/api/account/ledger")
                        .param("size", "5000")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void usersOnlySeeTheirOwnLedger() throws Exception {
        TestUser alice = TestUsers.create(mockMvc, objectMapper);
        TestUser bob = TestUsers.create(mockMvc, objectMapper);
        deposit(alice, "100.0000");

        mockMvc.perform(get("/api/account/ledger").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void accountEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account/ledger")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/account/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions deposit(TestUser user, String amount) throws Exception {
        return mockMvc.perform(post("/api/account/deposits")
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}"));
    }
}
