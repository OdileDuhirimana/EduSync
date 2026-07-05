package com.edusync.grading.domain;

/**
 * WHY an enum instead of the previous raw String status field: same
 * rationale as {@link GradeStatus} — DOM-03/DOM-04 flagged stringly-typed
 * status fields compared via string-literal equality with no compile-time
 * safety anywhere business logic branched on them.
 */
public enum RegradeStatus {
    PENDING,
    APPROVED,
    REJECTED
}
