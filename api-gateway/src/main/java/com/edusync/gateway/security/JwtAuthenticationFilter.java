package com.edusync.gateway.security;

import com.edusync.common.security.JwtVerifier;
import com.edusync.common.security.TrustedHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single trust boundary for the entire EduSync system.
 *
 * WHY this filter exists (fixes the #1 critical finding in both audit
 * reports — code-review.md SEC-02 and portfolio-evaluation.md AUTH-04):
 * previously, every downstream service trusted client-supplied
 * "X-User-Id" / "X-User-Roles" / "X-Tenant-Id" headers verbatim, and the
 * gateway performed zero JWT verification — so any anonymous HTTP client
 * could set "X-User-Roles: INSTRUCTOR" directly and impersonate any role.
 *
 * This filter now does two things on every request, in order:
 *   1. Unconditionally strips any client-supplied copies of the trusted
 *      identity headers (defense in depth: even if a service is somehow
 *      reached directly, forwarded requests never carry attacker-controlled
 *      values through the gateway).
 *   2. For any route not explicitly marked public, requires a valid
 *      "Authorization: Bearer <jwt>" header, cryptographically verifies its
 *      signature and expiry via {@link JwtVerifier}, and — only on success —
 *      re-injects the identity headers itself from the verified claims.
 *
 * Downstream services can therefore continue to read "X-User-Id" etc. (no
 * controller code in course-service/enrollment-service/etc. needs to change
 * to parse JWTs directly), but those headers are now only ever set by this
 * filter after a real signature check, closing the impersonation hole.
 *
 * ASSUMPTION (documented, not hidden): this trust model requires that
 * services are deployed so only the gateway is publicly reachable (a
 * standard microservices deployment topology). This is called out explicitly
 * in the README's "Security Model" section as a deployment requirement.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Path prefixes reachable without a verified token. Kept to the minimum
     * set required for the login/registration bootstrap flow and for
     * infrastructure health checks (load balancers, uptime monitors).
     */
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/actuator"
    );

    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest incoming = exchange.getRequest();
        String path = incoming.getURI().getPath();

        ServerHttpRequest.Builder strippedBuilder = incoming.mutate();
        TrustedHeaders.ALL.forEach(header -> strippedBuilder.headers(h -> h.remove(header)));

        if (isPublic(path)) {
            ServerHttpRequest strippedRequest = strippedBuilder.build();
            return chain.filter(exchange.mutate().request(strippedRequest).build());
        }

        String authorizationHeader = incoming.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = extractBearerToken(authorizationHeader);
        Optional<JwtVerifier.VerifiedIdentity> identity = jwtVerifier.verify(token);

        if (identity.isEmpty()) {
            log.warn("Rejected unauthenticated request path={} hasAuthHeader={}", path, authorizationHeader != null);
            return unauthorized(exchange, "AUTHENTICATION_REQUIRED", "A valid Bearer access token is required");
        }

        JwtVerifier.VerifiedIdentity verified = identity.get();
        ServerHttpRequest authenticatedRequest = strippedBuilder
                .header(TrustedHeaders.USER_ID, verified.userId())
                .header(TrustedHeaders.USER_EMAIL, verified.email() == null ? "" : verified.email())
                .header(TrustedHeaders.USER_ROLES, verified.rolesHeaderValue())
                .header(TrustedHeaders.TENANT_ID, verified.tenantId())
                .build();

        return chain.filter(exchange.mutate().request(authenticatedRequest).build());
    }

    private boolean isPublic(String path) {
        if (path.endsWith("/health")) {
            return true;
        }
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return authorizationHeader.substring(7).trim();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "code", code,
                "message", message,
                "status", HttpStatus.UNAUTHORIZED.value(),
                "path", exchange.getRequest().getURI().getPath(),
                "timestamp", Instant.now().toString()
        );
        byte[] bytes;
        try {
            bytes = OBJECT_MAPPER.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Must run before RequestLoggingFilter's response-logging completes and
        // before the request is routed to a downstream service, but stripping
        // headers first means order relative to logging is not security-critical.
        return -2;
    }
}
