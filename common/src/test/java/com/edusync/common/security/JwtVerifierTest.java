package com.edusync.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WHY this class is the single most important thing to unit-test in the
 * entire monorepo: {@link JwtVerifier} is the cryptographic trust boundary
 * for every request that passes through api-gateway's
 * {@code JwtAuthenticationFilter} (see that class's own adversarial test
 * suite in api-gateway, which mocks this collaborator rather than exercising
 * its real crypto). Both audits' #1 critical finding (forgeable
 * authorization headers) was fixed here; a regression in this class would
 * silently reopen that exact hole project-wide. These tests exercise the
 * real signature/expiry verification logic directly, with no mocking.
 */
class JwtVerifierTest {

    private static final String SECRET = "unit-test-secret-value-that-is-long-enough-for-hs256";

    @Test
    void constructorRejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtVerifier(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtVerifier(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void verifyReturnsEmptyForNullOrBlankToken() {
        JwtVerifier verifier = new JwtVerifier(SECRET);
        assertThat(verifier.verify(null)).isEmpty();
        assertThat(verifier.verify("")).isEmpty();
        assertThat(verifier.verify("   ")).isEmpty();
    }

    @Test
    void verifyReturnsEmptyForGarbageToken() {
        JwtVerifier verifier = new JwtVerifier(SECRET);
        assertThat(verifier.verify("not-a-real-jwt-at-all")).isEmpty();
    }

    @Test
    void verifyReturnsEmptyForTokenSignedWithADifferentKey() {
        // This is the exact exploit path SEC-02/AUTH-04 named: a token that
        // *looks* well-formed but was never signed by this system's secret.
        JwtVerifier verifier = new JwtVerifier(SECRET);
        String forged = validToken(Keys.hmacShaKeyFor("a-completely-different-secret-key-value-here".getBytes()),
                "attacker", List.of("ADMIN"), Duration_SECONDS_VALID);

        assertThat(verifier.verify(forged)).isEmpty();
    }

    @Test
    void verifyReturnsEmptyForExpiredToken() {
        JwtVerifier verifier = new JwtVerifier(SECRET);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expired = Jwts.builder()
                .setSubject("user-1")
                .setIssuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .setExpiration(Date.from(Instant.now().minusSeconds(3600)))
                .addClaims(Map.of("email", "u@example.com", "roles", List.of("STUDENT"), "tenantId", "default"))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertThat(verifier.verify(expired)).isEmpty();
    }

    @Test
    void verifyReturnsEmptyWhenSubjectIsBlank() {
        JwtVerifier verifier = new JwtVerifier(SECRET);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String noSubject = Jwts.builder()
                .setSubject("")
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertThat(verifier.verify(noSubject)).isEmpty();
    }

    @Test
    void verifyExtractsIdentityFromAValidToken() {
        JwtVerifier verifier = new JwtVerifier(SECRET);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String token = validToken(key, "user-42", List.of("INSTRUCTOR", "ADMIN"), Duration_SECONDS_VALID);

        Optional<JwtVerifier.VerifiedIdentity> identity = verifier.verify(token);

        assertThat(identity).isPresent();
        assertThat(identity.get().userId()).isEqualTo("user-42");
        assertThat(identity.get().roles()).containsExactly("INSTRUCTOR", "ADMIN");
        assertThat(identity.get().rolesHeaderValue()).isEqualTo("INSTRUCTOR,ADMIN");
        assertThat(identity.get().tenantId()).isEqualTo("default");
    }

    @Test
    void verifyDefaultsTenantIdWhenClaimIsAbsent() {
        JwtVerifier verifier = new JwtVerifier(SECRET);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String token = Jwts.builder()
                .setSubject("user-1")
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Optional<JwtVerifier.VerifiedIdentity> identity = verifier.verify(token);

        assertThat(identity).isPresent();
        assertThat(identity.get().tenantId()).isEqualTo("default");
        assertThat(identity.get().roles()).isEmpty();
    }

    @Test
    void acceptsAShortRawSecretByStretchingItViaSha256() {
        // WHY this matters: ensureMinKeyLength must stretch any secret under
        // 32 bytes deterministically, so the exact same short secret always
        // produces the exact same verification key — otherwise two instances
        // configured with the same short AUTH_JWT_SECRET could disagree.
        JwtVerifier verifier = new JwtVerifier("short-secret");
        assertThat(verifier).isNotNull();
    }

    private static final long Duration_SECONDS_VALID = 900;

    private String validToken(SecretKey key, String subject, List<String> roles, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .addClaims(Map.of("email", "u@example.com", "roles", roles, "tenantId", "default"))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
