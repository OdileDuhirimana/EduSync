package com.edusync.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * Verifies EduSync-issued HS256 access tokens.
 *
 * WHY this exists in `common`: every service and the gateway previously trusted
 * client-supplied "X-User-*" headers verbatim, which is a complete authorization
 * bypass (see code-review.md SEC-02 / portfolio-evaluation.md AUTH-04). The fix
 * requires the exact same signature-verification logic to run wherever a trust
 * decision is made, so it is centralized here instead of copy-pasted.
 *
 * ASSUMPTION: token issuance (auth-service) and token verification (gateway,
 * and potentially other services later) share one symmetric secret via the
 * `AUTH_JWT_SECRET` environment variable. This mirrors the existing HS256 design
 * documented as a known tradeoff in the README; migrating to RS256/JWKS is
 * flagged as follow-up work, not attempted here (out of scope per the "Fastest
 * Path to 90+" list, which only requires "real JWT verification", not an
 * algorithm change).
 */
public final class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(String secretValue) {
        if (secretValue == null || secretValue.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret must not be blank. Set the AUTH_JWT_SECRET environment variable.");
        }
        byte[] secretBytes;
        try {
            // WHY catch RuntimeException broadly here: jjwt's Base64 decoder
            // throws its own io.jsonwebtoken.io.DecodingException (a
            // JwtException subtype, not IllegalArgumentException) for
            // non-base64 input such as a plain-text secret containing
            // hyphens/underscores. Any decode failure simply means the
            // configured secret is a raw string rather than base64, so we
            // fall back to using its UTF-8 bytes directly.
            secretBytes = Decoders.BASE64.decode(secretValue);
        } catch (RuntimeException e) {
            secretBytes = secretValue.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(ensureMinKeyLength(secretBytes));
    }

    /**
     * Verifies the token signature and expiry. Returns empty (never throws) on
     * any failure so callers can fail closed uniformly with a 401, rather than
     * leak stack traces or exception types to the caller.
     */
    public Optional<VerifiedIdentity> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String subject = claims.getSubject();
            String email = claims.get("email", String.class);
            String tenantId = claims.get("tenantId", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedIdentity(
                    subject,
                    email,
                    roles == null ? List.of() : List.copyOf(roles),
                    tenantId == null || tenantId.isBlank() ? "default" : tenantId));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] ensureMinKeyLength(byte[] candidate) {
        if (candidate.length >= 32) {
            return candidate;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(candidate);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Identity extracted from a cryptographically verified access token. */
    public record VerifiedIdentity(String userId, String email, List<String> roles, String tenantId) {
        public String rolesHeaderValue() {
            return String.join(",", roles);
        }
    }
}
