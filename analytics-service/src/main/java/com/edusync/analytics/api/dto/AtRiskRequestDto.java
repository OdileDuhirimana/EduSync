package com.edusync.analytics.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * WHY learners is @NotEmpty: scoring a batch of zero learners produces no
 * useful output — that is a meaninglessly empty input (same rationale as
 * StudyPlanRequestDto#modules), so it is rejected with a 400 rather than
 * silently returning an empty result.
 *
 * WHY {@code List<@Valid LearnerSignalDto>}: @NotEmpty alone only checks the
 * list itself is non-empty; without the nested @Valid, Bean Validation would
 * never cascade into each LearnerSignalDto element and its
 * @DecimalMin/@DecimalMax constraints would silently never run.
 */
public record AtRiskRequestDto(
        String courseId,
        @NotEmpty List<@Valid LearnerSignalDto> learners
) {
}
