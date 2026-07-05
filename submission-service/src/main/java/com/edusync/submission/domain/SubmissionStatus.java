package com.edusync.submission.domain;

/**
 * WHY an enum instead of the previous raw String status field ("SUBMITTED"
 * string literal on the in-memory record with no other value ever produced):
 * DOM-03/DOM-04 in the audits flagged stringly-typed domain modeling across
 * the codebase (the same class of defect fixed in CourseStatus and
 * EnrollmentStatus). Only SUBMITTED exists today — there is no grading or
 * withdrawal workflow modeled in this service (that lives in
 * grading-service), so adding further states now would be speculative
 * (YAGNI). Extending this enum is a small, additive change if a future
 * requirement needs one.
 */
public enum SubmissionStatus {
    SUBMITTED
}
