package com.simulator.mdes.config;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Feign client configuration for all TSP → Core Banking HTTP calls.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li><b>Authentication</b>: Injects a short-lived (5 min) internal JWT bearer token
 *       into every outbound request via {@link InternalJwtRequestInterceptor}.</li>
 *   <li><b>Error handling</b>: Maps Core Banking 4xx / 5xx HTTP errors to typed
 *       exceptions via a custom {@link ErrorDecoder}.</li>
 * </ol>
 *
 * <p>The JWT is signed with HMAC-SHA256 using the shared secret configured via
 * {@code INTERNAL_JWT_SECRET} (injected as {@code internal.jwt.secret}).
 */
@Configuration
@Slf4j
public class FeignConfig {

    /** Short-lived internal JWT TTL — 5 minutes (ms). */
    private static final long INTERNAL_JWT_TTL_MS = 5 * 60 * 1_000L;

    @Value("${internal.jwt.secret}")
    private String jwtSecret;

    /**
     * Feign {@link RequestInterceptor} that mints and attaches a fresh internal JWT
     * on every outbound request to Core Banking.
     *
     * <p>The JWT claims:
     * <ul>
     *   <li>{@code sub} — {@code "mdes-tsp"} (service identifier)</li>
     *   <li>{@code role} — {@code "INTERNAL_SERVICE"}</li>
     *   <li>{@code iat} / {@code exp} — issued-at and expiry (5 min window)</li>
     * </ul>
     */
    @Bean
    public RequestInterceptor internalJwtRequestInterceptor() {
        return new InternalJwtRequestInterceptor(jwtSecret);
    }

    /**
     * Custom Feign error decoder: maps HTTP 4xx / 5xx responses from Core Banking
     * to meaningful runtime exceptions that propagate through the TSP service layer.
     */
    @Bean
    public ErrorDecoder coreBankingErrorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            String reason = response.reason();
            log.error("Core Banking call failed — method={}, status={}, reason={}", methodKey, status, reason);

            return switch (status) {
                case 400 -> new IllegalArgumentException("Core Banking rejected request: " + reason);
                case 401, 403 -> new SecurityException("Internal JWT rejected by Core Banking: " + reason);
                case 404 -> new com.simulator.mdes.exception.ResourceNotFoundException("Core Banking resource not found: " + reason);
                case 503 -> new com.simulator.mdes.exception.ServiceUnavailableException("Core Banking is unavailable: " + reason);
                default  -> new RuntimeException("Core Banking error [" + status + "]: " + reason);
            };
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner helper class
    // ─────────────────────────────────────────────────────────────────────────

    /** Stateless interceptor — builds and signs a new JWT on every call. */
    public static class InternalJwtRequestInterceptor implements RequestInterceptor {

        private final String secret;

        InternalJwtRequestInterceptor(String secret) {
            this.secret = secret;
        }

        @Override
        public void apply(feign.RequestTemplate template) {
            String token = buildJwt();
            template.header("Authorization", "Bearer " + token);
        }

        private String buildJwt() {
            long now = System.currentTimeMillis();
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

            return Jwts.builder()
                    .subject("mdes-tsp")
                    .claim("role", "INTERNAL_SERVICE")
                    .issuedAt(new Date(now))
                    .expiration(new Date(now + INTERNAL_JWT_TTL_MS))
                    .signWith(key)
                    .compact();
        }
    }
}
