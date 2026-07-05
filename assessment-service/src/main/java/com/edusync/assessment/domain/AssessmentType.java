package com.edusync.assessment.domain;

/**
 * WHY an enum instead of the previous raw String "type" field: the original
 * AssessmentController accepted an unvalidated `String type` and defaulted
 * silently to "QUIZ" whenever it was null, with no compile-time or run-time
 * guard against typos/unknown values. This mirrors CourseStatus/
 * EnrollmentStatus in the sibling services (DOM-03/DOM-04 in the audits):
 * stringly-typed domain fields are centralized into a type-safe enum, and
 * CreateAssessmentRequestDto now rejects unknown type strings with a 400
 * instead of silently defaulting (see that class's @Pattern constraint).
 */
public enum AssessmentType {
    QUIZ,
    EXAM,
    ASSIGNMENT
}
