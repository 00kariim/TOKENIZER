package com.simulator.mdes.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servlet filter that validates inbound JWT bearer tokens on every request.
 *
 * <p>The filter:
 * <ol>
 *   <li>Extracts the {@code Authorization: Bearer <token>} header.</li>
 *   <li>Validates signature and expiry using the shared {@code INTERNAL_JWT_SECRET}.</li>
 *   <li>On success: populates the {@link SecurityContextHolder} so downstream
 *       {@code @PreAuthorize} annotations and Spring Security rules can evaluate.</li>
 *   <li>On failure: returns {@code 401 Unauthorized} immediately without invoking
 *       the controller.</li>
 * </ol>
 *
 * <p>This filter handles <em>both</em> inbound tokens from the Nginx gateway
 * (Flutter-originated requests) and service-to-service tokens if Core Banking
 * ever needs to call back to MDES.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${internal.jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No bearer token — let Spring Security's authorization rules decide.
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            Claims claims = parseToken(token);

            String subject = claims.getSubject();
            String role    = claims.get("role", String.class);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            subject,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER")))
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT authenticated: sub={}, role={}", subject, role);

        } catch (JwtException ex) {
            log.warn("Invalid JWT on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or expired JWT token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
