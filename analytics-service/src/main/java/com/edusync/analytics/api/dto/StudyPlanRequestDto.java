package com.edusync.analytics.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * WHY weeklyHours/horizonDays use @Positive without @NotNull: null is a
 * legitimate "the caller has no preference" value — AnalyticsService#studyPlan
 * defaults a null weeklyHours to 8 and a null horizonDays to 14. Bean
 * Validation treats null as valid for every built-in constraint except
 * @NotNull, so @Positive alone lets that default-if-absent path through
 * untouched while still rejecting a caller-supplied zero or negative value
 * with a 400 instead of silently clamping it to 1 (the previous behavior,
 * which hid a caller bug instead of reporting it). This is the "reject if
 * invalid, default if absent" split described in the class-level rationale
 * shared with AtRiskRequestDto/GradeForecastRequestDto.
 *
 * WHY modules is @NotEmpty: a study plan request with zero modules has
 * nothing to schedule. That is a meaninglessly empty input, not a
 * legitimate "use a default" case, so it is rejected outright with a 400
 * rather than silently returning an empty schedule.
 */
public record StudyPlanRequestDto(
        String learnerId,
        @Positive Integer weeklyHours,
        @Positive Integer horizonDays,
        @NotEmpty List<StudyModuleDto> modules
) {
}
