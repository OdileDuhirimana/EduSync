package com.edusync.grading.domain;

/**
 * WHY an enum instead of the previous raw String status field on the
 * in-memory `GradeRecord` record: the original controller compared status
 * via string literals ("GRADED"/"GRADED_OVERRIDDEN") with no compile-time
 * safety, the same class of stringly-typed domain modeling DOM-03/DOM-04
 * flagged in course-service/enrollment-service before this remediation.
 *
 * WHY only two values (stricter than a hypothetical DRAFT/UNGRADED state):
 * a {@link GradeRecord} row is only ever created once a grade actually
 * exists (auto-grade or manual-grade); there is no "ungraded" row to model,
 * so GRADED / GRADED_OVERRIDDEN fully covers the domain.
 */
public enum GradeStatus {
    GRADED,
    GRADED_OVERRIDDEN
}
