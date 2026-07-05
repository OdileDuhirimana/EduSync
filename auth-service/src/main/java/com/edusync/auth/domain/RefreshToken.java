package com.edusync.auth.domain;

import java.time.Instant;

/** Refresh-token session record, extracted out of AuthController for the same reason as {@link User}. */
public record RefreshToken(String token, String userId, String tenantId, Instant issuedAt) {
}
