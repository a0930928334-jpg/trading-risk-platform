package com.shuhongyi.trading.config;

import com.shuhongyi.trading.auth.CurrentUserId;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata and the JWT bearer scheme. Every operation requires the bearer token
 * unless it declares an empty {@code @SecurityRequirements}.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    static {
        // The user ID comes from the JWT, never from the request, so it must not appear as a parameter.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    @Bean
    public OpenAPI tradingRiskPlatformOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trading & Risk Platform API")
                        .version("0.1.0")
                        .description("""
                                Simulated equities trading back end: JWT authentication, cash ledger with reservations, \
                                limit orders with pre-trade risk checks, and an in-memory price-time priority matching \
                                engine backed by PostgreSQL. Instruments and prices are simulated. \
                                Errors are returned as RFC 9457 problem details.""")
                        .license(new License().name("Portfolio project")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/auth/login (valid for 1 hour)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
