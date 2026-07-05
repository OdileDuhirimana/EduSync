package com.edusync.gateway.security;

import com.edusync.common.security.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityConfig {

    /**
     * WHY no hardcoded fallback secret here (unlike the pre-existing
     * auth-service default): SEC-04 in the code review flagged
     * "changemechangemechangemechangeme" as a hardcoded default shipped in
     * source control. The gateway is the newly created trust boundary, so it
     * is the right place to stop the anti-pattern from spreading further —
     * this bean intentionally has no default and fails fast at startup if
     * AUTH_JWT_SECRET is not supplied, forcing every environment (including
     * local dev) to set it explicitly. See README "Running locally" for the
     * one-line export needed.
     */
    @Bean
    public JwtVerifier jwtVerifier(@Value("${auth.jwt.secret}") String secret) {
        return new JwtVerifier(secret);
    }
}
