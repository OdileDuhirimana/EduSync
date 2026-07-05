package com.edusync.analytics.api.dto;

/**
 * WHY no validation annotations on estimatedMinutes/difficulty/dueDate: each
 * is individually defaulted to a sensible value when absent or out of range
 * by AnalyticsService#studyPlan's existing {@code bounded()}/
 * {@code parseDueDate()} helpers (e.g. a missing difficulty defaults to 3,
 * an unparsable dueDate falls back to the plan horizon's end) — a
 * malformed value here does not make the request meaningless the way a
 * completely empty module list does (see StudyPlanRequestDto), it just falls
 * back to a reasonable assumption, so "default if absent" is the correct
 * strategy for these fields rather than rejecting the whole request.
 */
public record StudyModuleDto(
        String moduleId,
        String title,
        Integer estimatedMinutes,
        Integer difficulty,
        String dueDate
) {
}
