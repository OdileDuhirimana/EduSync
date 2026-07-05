package com.edusync.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single raw engagement signal (a page view or an active-session ping) for
 * one course/user pair.
 *
 * WHY this entity — and its two siblings {@link GradeRecordSnapshot} and
 * {@link FunnelEvent} — exist at all: the code review's Critical Issue was
 * that /engagement, /grade-distribution and /funnels returned hardcoded
 * static numbers ("dau": 42) no matter what courseId was passed in. This
 * monorepo pass has no upstream event stream anywhere else in the system for
 * analytics-service to aggregate from, so the only honest fix — rather than
 * leaving the endpoints mocked, or inventing a fake cross-service
 * integration that does not exist anywhere else in this codebase — is for
 * analytics-service to own real persistence for the raw events it reports
 * on, expose ingestion endpoints for them, and compute its read endpoints as
 * genuine aggregation queries over that persisted data. See
 * AnalyticsService#engagement for the query this entity backs, and
 * V1__create_analytics_events_tables.sql for the schema/indexes.
 */
@Entity
@Table(name = "engagement_events")
public class EngagementEvent {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EngagementEventType eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected EngagementEvent() {
        // required by JPA
    }

    public EngagementEvent(String id, String courseId, String userId, EngagementEventType eventType, Instant occurredAt) {
        this.id = id;
        this.courseId = courseId;
        this.userId = userId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getUserId() {
        return userId;
    }

    public EngagementEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
