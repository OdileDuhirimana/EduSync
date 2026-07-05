package com.edusync.enrollment.domain;

/**
 * WHY an enum instead of the previous raw String status field (was a bare
 * "ENROLLED" string literal on the in-memory record with no other value ever
 * produced): DOM-03/DOM-04 in the audits flagged stringly-typed domain
 * modeling across the codebase. DROPPED is a first-class state (see
 * Enrollment#drop) rather than row deletion — see the WHY comment on
 * Enrollment for the reasoning behind that choice.
 */
public enum EnrollmentStatus {
    ENROLLED,
    DROPPED
}
