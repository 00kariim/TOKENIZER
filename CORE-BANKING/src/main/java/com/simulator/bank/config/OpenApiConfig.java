package com.simulator.bank.config;

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
 * OpenAPI 3.1 / Swagger UI configuration for the Core Banking Simulator.
 *
 * <p>Swagger UI is available at:
 * <ul>
 *   <li>{@code http://localhost:8082/swagger-ui.html} (when debug port is published)</li>
 *   <li>{@code http://CORE-BANKING:8082/swagger-ui.html} (Docker internal)</li>
 *   <li>{@code http://CORE-BANKING:8082/v3/api-docs} (raw JSON)</li>
 * </ul>
 *
 * <p>All {@code /api/**} endpoints are protected by the MDES internal JWT. The Bearer
 * scheme is pre-registered so the Swagger UI "Authorize" dialog works out of the box.
 *
 * <p><b>Note:</b> In production, port 8082 is NOT published to the host. To access
 * Swagger UI, either temporarily enable the debug port in docker-compose.yml or
 * route through the MDES container.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "Bearer JWT (Internal)";

    @Bean
    public OpenAPI coreBankingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Core Banking Simulator API")
                        .version("1.0.0")
                        .description("""
                                **Card Issuer Simulator** — handles ID&V, OTP delivery, payment \
                                authorisation, and account ledger management.

                                ### ⚠ Internal Service
                                These endpoints are **exclusively called by the MDES TSP Simulator** \
                                via service-to-service JWT authentication. Direct external access is \
                                blocked at the Docker network layer.

                                ### Authentication
                                All `/api/**` endpoints require an `INTERNAL_SERVICE` Bearer JWT \
                                minted by the MDES FeignConfig interceptor. Use the **Authorize** \
                                button to set a test token.

                                ### Provisioning flow
                                | Step | Endpoint |
                                |---|---|
                                | 1. ID&V | `POST /api/v1/core/authorizeService` |
                                | 2. OTP send | `POST /api/v1/core/deliverActivationCode` |
                                | 3. Confirm | `POST /api/v1/core/notifyServiceActivated` |

                                ### Payment flow
                                | Step | Endpoint |
                                |---|---|
                                | Authorise | `POST /api/v1/core/authorizeTransaction` |
                                """)
                        .contact(new Contact()
                                .name("Payment Tokenization Simulator")
                                .email("simulator@internal.dev"))
                        .license(new License()
                                .name("Internal — Not for distribution")))
                .servers(List.of(
                        new Server().url("http://CORE-BANKING:8082").description("Docker internal (default)"),
                        new Server().url("http://localhost:8082").description("Debug — only when port published")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("5-minute HMAC-SHA256 JWT minted by MDES FeignConfig. " +
                                                     "sub=mdes-tsp, role=INTERNAL_SERVICE.")));
    }
}
