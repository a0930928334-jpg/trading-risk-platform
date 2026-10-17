package com.shuhongyi.trading.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Places orders through the public API and returns the response body.
 */
public final class TestOrders {

    private TestOrders() {
    }

    public static JsonNode place(
            MockMvc mockMvc, ObjectMapper objectMapper, TestUser user,
            String symbol, String side, String price, long quantity) throws Exception {
        String body = """
                {"symbol": "%s", "side": "%s", "price": %s, "quantity": %d}
                """.formatted(symbol, side, price, quantity);
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
