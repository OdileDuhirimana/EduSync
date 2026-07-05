package com.edusync.analytics.domain;

/**
 * WHY an enum instead of a raw String event type: mirrors the domain
 * modeling convention already established by CourseStatus/EnrollmentStatus
 * in the sibling services — a typo in an event type fails to compile (or
 * fails Jackson deserialization with a clear 400) instead of silently
 * creating a new, never-aggregated bucket of events.
 */
public enum EngagementEventType {
    VIEW,
    ACTIVE_SESSION
}
