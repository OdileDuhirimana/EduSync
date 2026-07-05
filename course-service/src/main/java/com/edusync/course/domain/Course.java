package com.edusync.course.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity backing real persistence for courses.
 *
 * WHY this replaces the previous `record Course(...)` held in a
 * ConcurrentHashMap field on the controller: that design lost all data on
 * every restart and could not run as more than one instance (DB-01 through
 * DB-08, SEC-01, SCALE-05 in the audits). This entity is mapped by Flyway
 * migration V1__create_courses_table.sql, which is the single source of
 * truth for the schema (see that file for column constraints/indexes).
 */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    /**
     * WHY this column exists (closes SEC-04 in the portfolio evaluation:
     * "course-service's Course entity has no owner/instructor-id field at
     * all... any instructor can publish any other instructor's course"): a
     * role check alone (INSTRUCTOR/ADMIN) only proves *what kind* of user the
     * caller is, not that they are the specific instructor who owns this
     * specific course. Recording the creating instructor's verified
     * {@code X-User-Id} at creation time lets {@link
     * com.edusync.course.service.CourseService#publish} enforce a real
     * per-resource ownership check, not just a per-role one.
     */
    @Column(name = "instructor_id", nullable = false, length = 64)
    private String instructorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CourseStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Course() {
        // required by JPA
    }

    public Course(String id, String code, String title, String instructorId, CourseStatus status,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.instructorId = instructorId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructorId() {
        return instructorId;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void publish(Instant when) {
        this.status = CourseStatus.PUBLISHED;
        this.updatedAt = when;
    }
}
