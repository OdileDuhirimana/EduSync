package com.edusync.gateway.security;

import com.edusync.common.security.JwtVerifier;
import com.edusync.common.security.TrustedHeaders;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the gateway's single most security-critical component.
 *
 * These are true unit tests (Mockito-mocked GatewayFilterChain, no Spring
 * context) covering the exact scenario the audits flagged as a critical
 * vulnerability: a client attempting to set X-User-Roles/X-User-Id directly
 * must never have that value reach a downstream service.
 */
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "unit-test-secret-value-that-is-long-enough-for-hs256";

    private JwtAuthenticationFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(new JwtVerifier(TEST_SECRET));
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void rejectsRequestWithNoAuthorizationHeader() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/courses").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void rejectsRequestWithForgedIdentityHeadersAndNoToken() {
        // This is the exact exploit path from SEC-02 / AUTH-04: a client
        // setting X-User-Roles directly with no token at all.
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/courses")
                        .header(TrustedHeaders.USER_ROLES, "INSTRUCTOR")
                        .header(TrustedHeaders.USER_ID, "attacker-supplied-id")
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void rejectsRequestWithInvalidSignature() {
        String tokenSignedWithDifferentKey = validAccessToken(
                Keys.hmacShaKeyFor("a-completely-different-secret-key-value-here".getBytes()),
                "user-1", List.of("STUDENT"));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/courses")
                        .header("Authorization", "Bearer " + tokenSignedWithDifferentKey)
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void rejectsExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
        String expired = Jwts.builder()
                .setSubject("user-1")
                .setIssuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .setExpiration(Date.from(Instant.now().minusSeconds(3600)))
                .addClaims(Map.of("email", "u@example.com", "roles", List.of("STUDENT"), "tenantId", "default"))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/courses")
                        .header("Authorization", "Bearer " + expired)
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsValidTokenAndInjectsVerifiedIdentityHeaders() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
        String token = validAccessToken(key, "user-42", List.of("INSTRUCTOR"));

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/courses")
                        .header("Authorization", "Bearer " + token)
                        // client also tries to spoof a different identity —
                        // this must be overwritten, not merged or trusted.
                        .header(TrustedHeaders.USER_ID, "someone-else")
                        .header(TrustedHeaders.USER_ROLES, "ADMIN")
                        .build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(mutatedExchange -> {
            var headers = mutatedExchange.getRequest().getHeaders();
            return "user-42".equals(headers.getFirst(TrustedHeaders.USER_ID))
                    && "INSTRUCTOR".equals(headers.getFirst(TrustedHeaders.USER_ROLES))
                    && "default".equals(headers.getFirst(TrustedHeaders.TENANT_ID));
        }));
    }

    @Test
    void publicHealthRouteBypassesAuthenticationButStillStripsForgedHeaders() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/courses/health")
                        .header(TrustedHeaders.USER_ROLES, "ADMIN")
                        .build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(argThat(mutatedExchange ->
                mutatedExchange.getRequest().getHeaders().getFirst(TrustedHeaders.USER_ROLES) == null));
    }

    @Test
    void publicLoginRouteBypassesAuthentication() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    private String validAccessToken(SecretKey key, String subject, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(900)))
                .addClaims(Map.of("email", "u@example.com", "roles", roles, "tenantId", "default"))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
