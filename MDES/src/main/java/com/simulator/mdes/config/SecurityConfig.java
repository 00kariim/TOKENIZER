package com.simulator.mdes.config;

import com.simulator.mdes.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the MDES TSP Simulator.
 *
 * <p><b>Inbound security model</b>:
 * <ul>
 *   <li>Requests arriving from the Nginx gateway carry a JWT signed by the gateway.
 *       The {@link JwtAuthenticationFilter} validates this token before any endpoint
 *       handler is invoked.</li>
 *   <li>Actuator health endpoints are open to allow Docker / k8s liveness probes.</li>
 *   <li>All other endpoints require a valid bearer token.</li>
 * </ul>
 *
 * <p><b>Outbound security</b>: handled by {@link FeignConfig.InternalJwtRequestInterceptor},
 * which attaches a freshly minted service-to-service JWT to every Core Banking call.
 *
 * <p>Session management is {@code STATELESS} — no HTTP session is created or used.
 * CSRF protection is disabled because the API is consumed by non-browser clients
 * (Flutter SDK, Feign).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless REST API — disable session creation.
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF not needed for JWT-authenticated stateless APIs.
            .csrf(AbstractHttpConfigurer::disable)

            // Authorization rules.
            .authorizeHttpRequests(auth -> auth
                // Docker / k8s liveness and readiness probes.
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness"
                ).permitAll()
                // Swagger UI + OpenAPI spec — open for developer access.
                .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml"
                ).permitAll()
                // RSA public key — must be accessible by Flutter without a token.
                .requestMatchers("/api/v1/mdes/publicKey").permitAll()
                // All other /api/** endpoints require a valid JWT.
                .requestMatchers("/api/**").authenticated()
                // Deny anything else.
                .anyRequest().denyAll()
            )

            // Validate the inbound JWT before the standard auth filter.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
