package com.edusync.course.domain;

/**
 * WHY an enum instead of the previous raw String status field: the original
 * controller compared status via string literals ("DRAFT"/"PUBLISHED") with
 * no compile-time safety. DOM-03/DOM-04 in the audits flagged this class of
 * stringly-typed domain modeling across the codebase.
 */
public enum CourseStatus {
    DRAFT,
    PUBLISHED
}
