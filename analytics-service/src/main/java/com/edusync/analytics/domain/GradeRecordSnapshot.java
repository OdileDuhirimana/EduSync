package com.edusync.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single recorded letter grade for one course/user pair, at the point in
 * time it was recorded. See {@link EngagementEvent}'s Javadoc for why this
 * entity (real, persisted, ingested data) replaces the previous hardcoded
 * /grade-distribution response.
 */
@Entity
@Table(name = "grade_record_snapshots")
public class GradeRecordSnapshot {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "letter_grade", nullable = false, length = 8)
    private LetterGrade letterGrade;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected GradeRecordSnapshot() {
        // required by JPA
    }

    public GradeRecordSnapshot(String id, String courseId, String userId, LetterGrade letterGrade, Instant recordedAt) {
        this.id = id;
        this.courseId = courseId;
        this.userId = userId;
        this.letterGrade = letterGrade;
        this.recordedAt = recordedAt;
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

    public LetterGrade getLetterGrade() {
        return letterGrade;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
