package com.edusync.analytics.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * WHY @DecimalMin/@DecimalMax on completionRate/averageScore instead of
 * relying solely on AnalyticsService#bounded()'s manual clamping: a
 * caller-supplied completionRate of, say, 4.0 (400%) is a malformed request
 * that deserves an explicit 400 telling the caller the valid range, not a
 * silent clamp to 1.0 that hides their bug and produces a misleadingly
 * "successful" risk score. bounded() is still applied afterwards in
 * AnalyticsService, but purely as defense in depth for any future caller
 * that reaches the service directly (bypassing HTTP validation) and to
 * supply a sensible default when a field is entirely absent (null) — these
 * are deliberately two different validation strategies for two different
 * situations (value present but out of range vs. value absent), not a
 * duplicate of the same check flagged as inconsistent by the audit.
 */
public record LearnerSignalDto(
        String userId,
        @DecimalMin("0.0") @DecimalMax("1.0") Double completionRate,
        @DecimalMin("0.0") @DecimalMax("100.0") Double averageScore,
        @PositiveOrZero Integer lastActiveDaysAgo,
        @PositiveOrZero Integer missedDeadlines
) {
}
