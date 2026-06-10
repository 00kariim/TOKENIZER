package com.simulator.mdes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.1 / Swagger UI configuration for the MDES TSP Simulator.
 *
 * <p>Swagger UI is available at:
 * <ul>
 *   <li>{@code http://localhost:8081/swagger-ui.html}</li>
 *   <li>{@code http://localhost:8081/v3/api-docs} (raw JSON)</li>
 * </ul>
 *
 * <p>The "Bearer JWT" security scheme is pre-wired so testers can paste a
 * token directly into the Swagger UI "Authorize" button and call protected
 * endpoints without leaving the browser.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "Bearer JWT";

    @Bean
    public OpenAPI mdesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MDES TSP Simulator API")
                        .version("1.0.0")
                        .description("""
                                **EMVCo-Compliant Token Service Provider** — simulates Mastercard MDES \
                                token provisioning, cryptogram generation, and de-tokenization flows.

                                ### Authentication
                                All `/api/**` endpoints require a Bearer JWT in the `Authorization` header.
                                Use the **Authorize** button above to set your token.

                                ### Key flows
                                | Flow | Endpoints |
                                |---|---|
                                | Provisioning | `POST /api/v1/mdes/provisioning/tokenize` → `POST /activate` |
                                | Payment | `POST /api/v1/mdes/transaction/authorize` |
                                | Lifecycle | `GET /token/{value}/status` · `PUT /token/{value}/lifecycle` |
                                """)
                        .contact(new Contact()
                                .name("Payment Tokenization Simulator")
                                .email("simulator@internal.dev"))
                        .license(new License()
                                .name("Internal — Not for distribution")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local Docker"),
                        new Server().url("http://MDES:8081").description("Docker internal")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Internal JWT signed with INTERNAL_JWT_SECRET (HMAC-SHA256). " +
                                                     "TTL: 5 minutes.")));
    }
}
