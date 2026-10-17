package com.shuhongyi.trading.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuhongyi.trading.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OpenApiDocumentationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void documentsEveryEndpointWithBearerAuthentication() throws Exception {
        JsonNode api = apiDocs();

        assertThat(api.at("/info/title").asText()).isEqualTo("Trading & Risk Platform API");
        assertThat(api.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(api.at("/paths").fieldNames()).toIterable().contains(
                "/api/auth/register",
                "/api/auth/login",
                "/api/me",
                "/api/account",
                "/api/account/deposits",
                "/api/account/ledger",
                "/api/instruments",
                "/api/instruments/{symbol}/book",
                "/api/orders",
                "/api/orders/{id}",
                "/api/trades",
                "/api/positions");
        assertThat(api.at("/paths/~1api~1orders/post/summary").asText()).isEqualTo("Place a limit order");
    }

    @Test
    void loginAndRegisterNeedNoTokenInTheDocument() throws Exception {
        JsonNode api = apiDocs();

        for (String path : List.of("/~1api~1auth~1login/post", "/~1api~1auth~1register/post")) {
            JsonNode security = api.at("/paths" + path + "/security");
            assertThat(security.isArray()).as("explicit empty security on %s", path).isTrue();
            assertThat(security).isEmpty();
        }
        assertThat(api.at("/paths/~1api~1orders/post/security").isMissingNode()).isTrue();
        assertThat(api.at("/security/0/bearerAuth").isArray()).isTrue();
    }

    @Test
    void currentUserIdIsNotExposedAsARequestParameter() throws Exception {
        JsonNode api = apiDocs();

        for (String path : List.of("/~1api~1me/get", "/~1api~1account/get", "/~1api~1orders/post")) {
            JsonNode parameters = api.at("/paths" + path + "/parameters");
            parameters.forEach(parameter -> assertThat(parameter.get("name").asText()).isNotEqualTo("userId"));
        }
    }

    private JsonNode apiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
