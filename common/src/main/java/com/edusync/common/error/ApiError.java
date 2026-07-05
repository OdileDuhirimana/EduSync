package com.edusync.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error envelope for every service.
 *
 * WHY: the audit (API-02/API-04) found every controller hand-rolling its own
 * `Map.of("error", ...)` body with inconsistent shapes (some had "message",
 * some didn't; HTTP status literals vs. HttpStatus constants varied). This
 * record is the single shape all services now return via GlobalExceptionHandler.
 */
public record ApiError(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp,
        List<FieldViolation> fieldErrors
) {
    public static ApiError of(String code, String message, int status, String path) {
        return new ApiError(code, message, status, path, Instant.now(), List.of());
    }

    public static ApiError ofFieldErrors(String path, int status, List<FieldViolation> violations) {
        return new ApiError("VALIDATION_ERROR", "Request validation failed", status, path, Instant.now(), violations);
    }

    public record FieldViolation(String field, String message) {
    }
}
