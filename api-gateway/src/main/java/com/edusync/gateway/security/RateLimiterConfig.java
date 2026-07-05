package com.edusync.gateway.security;

import com.edusync.common.security.TrustedHeaders;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * WHY a per-caller key rather than a single global bucket (item 10 of the
 * remediation plan, SEC-09 in the code review): the gateway's
 * {@link JwtAuthenticationFilter} runs before routing and, for any
 * authenticated route, has already injected a verified {@code X-User-Id}
 * header by the time this resolver runs. Keying the Redis-backed rate
 * limiter on that verified user id means one abusive account is throttled
 * without punishing every other concurrent user of the system — a single
 * shared bucket would let one noisy client exhaust the limit for everyone.
 *
 * For the handful of routes {@link JwtAuthenticationFilter} treats as public
 * (login/register/refresh, health checks), there is no verified user id yet,
 * so this falls back to the caller's remote IP address. This still protects
 * against unauthenticated brute-force/credential stuffing against
 * {@code /auth/login} and {@code /auth/register}, which is exactly the
 * traffic class an unauthenticated rate limit needs to cover.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver rateLimiterKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(TrustedHeaders.USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just(userId);
            }
            String remoteAddress = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(remoteAddress);
        };
    }
}
