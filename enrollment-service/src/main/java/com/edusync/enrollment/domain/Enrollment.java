package com.edusync.enrollment.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity backing real persistence for enrollments.
 *
 * WHY this replaces the previous `record Enrollment(...)` held in a
 * ConcurrentHashMap field on the controller: that design lost all data on
 * every restart and could not run as more than one instance (DB-01 through
 * DB-08, SEC-01, SCALE-05 in the audits). This entity is mapped by Flyway
 * migration V1__create_enrollments_table.sql, which is the single source of
 * truth for the schema (see that file for column constraints/indexes).
 *
 * WHY drop() is a status transition (soft delete) rather than a repository
 * delete of the row: an LMS enrollment history is a defensible audit trail —
 * "was this student ever enrolled in this course, and when did they drop" is
 * a question a real registrar/compliance workflow needs to answer later, and
 * a hard DELETE destroys that evidence irrecoverably. This mirrors the same
 * "never destroy history you might need later" reasoning as CourseService's
 * publish() transition (a status change, not a replacement row). The
 * tradeoff is that the (tenant, course, user) uniqueness rule below has to be
 * enforced as "no other ENROLLED row for this triple" rather than a bare
 * table-wide unique constraint — see V1__create_enrollments_table.sql and
 * EnrollmentService#create for how re-enrollment after a drop is handled.
 */
@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnrollmentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Enrollment() {
        // required by JPA
    }

    public Enrollment(String id, String tenantId, String courseId, String userId,
                       EnrollmentStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.courseId = courseId;
        this.userId = userId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getUserId() {
        return userId;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void drop(Instant when) {
        this.status = EnrollmentStatus.DROPPED;
        this.updatedAt = when;
    }

    public void reactivate(Instant when) {
        this.status = EnrollmentStatus.ENROLLED;
        this.updatedAt = when;
    }
}
