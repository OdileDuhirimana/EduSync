package com.edusync.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single course-funnel stage transition (view / enroll / complete) for one
 * course. See {@link EngagementEvent}'s Javadoc for why this entity (real,
 * persisted, ingested data) replaces the previous hardcoded /funnels
 * response.
 */
@Entity
@Table(name = "funnel_events")
public class FunnelEvent {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 16)
    private FunnelStage stage;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected FunnelEvent() {
        // required by JPA
    }

    public FunnelEvent(String id, String courseId, FunnelStage stage, Instant occurredAt) {
        this.id = id;
        this.courseId = courseId;
        this.stage = stage;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public String getCourseId() {
        return courseId;
    }

    public FunnelStage getStage() {
        return stage;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
