package com.shuhongyi.trading.instrument;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.support.IntegrationTest;
import com.shuhongyi.trading.support.TestUser;
import com.shuhongyi.trading.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class InstrumentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listsSeededInstrumentsWithReferencePrices() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        mockMvc.perform(get("/api/instruments")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].symbol").value(hasItems("AAPL", "MSFT", "NVDA", "TSLA", "AMZN")))
                .andExpect(jsonPath("$.content[?(@.symbol == 'AAPL')].referencePrice").value(hasItems(190.0)))
                .andExpect(jsonPath("$.content[?(@.symbol == 'AAPL')].status").value(hasItems("ACTIVE")))
                .andExpect(jsonPath("$.content[?(@.symbol == 'AAPL')].currency").value(hasItems("USD")));
    }

    @Test
    void instrumentListIsPaginated() throws Exception {
        TestUser user = TestUsers.create(mockMvc, objectMapper);

        mockMvc.perform(get("/api/instruments")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void instrumentsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/instruments")).andExpect(status().isUnauthorized());
    }
}
