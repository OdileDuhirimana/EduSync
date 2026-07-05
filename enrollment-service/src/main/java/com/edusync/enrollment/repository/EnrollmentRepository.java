package com.edusync.enrollment.repository;

import com.edusync.enrollment.domain.Enrollment;
import com.edusync.enrollment.domain.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * WHY a repository interface exists at all: ARC-01/ARC-03/AR-03 in both
 * audits flagged that no repository abstraction existed anywhere in the
 * codebase — controllers held ConcurrentHashMap fields directly. Spring Data
 * generates the implementation; EnrollmentService depends on this interface
 * (not a concrete Map), which is what makes EnrollmentService unit-testable
 * in isolation with a Mockito mock (see EnrollmentServiceTest).
 */
public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {

    /**
     * WHY paginated (closes API-05/06/07 from the audits): the previous
     * `/enrollments/me` endpoint returned the entire unbounded in-memory
     * collection filtered in Java on every call. This delegates both the
     * filter and the pagination to the database via a derived query, backed
     * by the ix_enrollments_tenant_user index (see the Flyway migration).
     */
    Page<Enrollment> findByTenantIdAndUserId(String tenantId, String userId, Pageable pageable);

    /**
     * WHY queried by status too, not just the (tenant, course, user) triple:
     * a prior DROPPED enrollment for the same triple must not block a new
     * enrollment attempt — only a currently ENROLLED row represents an active
     * conflict. Used by EnrollmentService#create as the defense-in-depth
     * application-level check that backs the DB-level constraint described in
     * V1__create_enrollments_table.sql.
     */
    Optional<Enrollment> findByTenantIdAndCourseIdAndUserIdAndStatus(
            String tenantId, String courseId, String userId, EnrollmentStatus status);
}
