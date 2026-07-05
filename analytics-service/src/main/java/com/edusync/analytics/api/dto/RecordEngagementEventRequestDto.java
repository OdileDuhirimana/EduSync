package com.edusync.analytics.api.dto;

import com.edusync.analytics.domain.EngagementEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * WHY every field here is "reject if invalid" (@NotBlank/@NotNull) rather
 * than defaulted: an engagement event with no course, no user, or no event
 * type is not a legitimate signal — there is no sensible default that would
 * make it meaningful to persist and later aggregate. Contrast this with
 * StudyPlanRequestDto's weeklyHours/horizonDays, which are genuinely optional
 * caller preferences with real defaults (see AnalyticsService#studyPlan).
 */
public record RecordEngagementEventRequestDto(
        @NotBlank String courseId,
        @NotBlank String userId,
        @NotNull EngagementEventType eventType
) {
}
