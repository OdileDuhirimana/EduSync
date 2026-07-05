package com.edusync.analytics.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.util.List;

/**
 * WHY completed/remaining have no @NotEmpty (unlike StudyPlanRequestDto's
 * modules or AtRiskRequestDto's learners): an empty completed list is a
 * legitimate "the learner hasn't finished any graded work yet" state, and an
 * empty remaining list is a legitimate "the course is over, nothing left to
 * forecast" state — both are meaningful, computable inputs (see
 * AnalyticsService#gradeForecast's division-by-zero guards), not
 * meaninglessly empty requests, so they are left optional rather than
 * rejected.
 */
public record GradeForecastRequestDto(
        String learnerId,
        String courseId,
        List<@Valid CompletedGradeDto> completed,
        List<@Valid RemainingGradeDto> remaining,
        @DecimalMin("0.0") @DecimalMax("100.0") Double targetFinalGrade
) {
}
