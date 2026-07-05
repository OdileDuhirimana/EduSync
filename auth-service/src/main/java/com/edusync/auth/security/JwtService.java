package com.edusync.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtService(
            // WHY no default here (fixes Critical Issue #3 / SEC-04 from the
            // code review: a hardcoded fallback secret "changemechangeme..."
            // was previously committed to source control and used whenever
            // AUTH_JWT_SECRET was unset). Requiring the property with no
            // default makes Spring fail application startup immediately if
            // the secret is missing, rather than silently signing tokens
            // with a publicly known key. Set it locally with:
            //   export AUTH_JWT_SECRET=$(openssl rand -base64 32)
            @Value("${auth.jwt.secret}") String secretValue,
            @Value("${auth.jwt.accessTtlSeconds:900}") long accessTtlSeconds
    ) {
        if (secretValue == null || secretValue.isBlank()) {
            throw new IllegalStateException(
                    "auth.jwt.secret (AUTH_JWT_SECRET) must be set; refusing to start with no signing key.");
        }
        byte[] secretBytes;
        try {
            // Try base64 first
            secretBytes = Decoders.BASE64.decode(secretValue);
        } catch (Exception e) {
            // Fallback to raw string bytes (UTF-8)
            secretBytes = secretValue.getBytes(StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(ensureMinKeyLength(secretBytes));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String createAccessToken(String subject, String email, List<String> roles, String tenantId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTtlSeconds);
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .addClaims(Map.of(
                        "email", email,
                        "roles", roles,
                        "tenantId", tenantId
                ))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
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
}
