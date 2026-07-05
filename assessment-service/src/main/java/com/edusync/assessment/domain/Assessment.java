package com.edusync.assessment.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity backing real persistence for assessments (quizzes, exams,
 * assignments).
 *
 * WHY this replaces the previous `Map<String, Object>` held in a
 * ConcurrentHashMap field on the controller: that design lost all data on
 * every restart and could not run as more than one instance (the same
 * DB-01 through DB-08, SEC-01, SCALE-05 class of defect course-service and
 * enrollment-service were remediated for). This entity is mapped by Flyway
 * migration V1__create_assessments_table.sql, which is the single source of
 * truth for the schema (see that file for column constraints/indexes).
 */
@Entity
@Table(name = "assessments")
public class Assessment {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssessmentType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Assessment() {
        // required by JPA
    }

    public Assessment(String id, String courseId, String title, AssessmentType type,
                       Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.courseId = courseId;
        this.title = title;
        this.type = type;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public AssessmentType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
