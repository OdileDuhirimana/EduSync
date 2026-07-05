package com.edusync.enrollment.service;

import com.edusync.common.error.BadRequestException;
import com.edusync.common.error.ConflictException;
import com.edusync.common.error.ForbiddenException;
import com.edusync.common.error.NotFoundException;
import com.edusync.enrollment.client.CourseClient;
import com.edusync.enrollment.client.CourseSummary;
import com.edusync.enrollment.domain.Enrollment;
import com.edusync.enrollment.domain.EnrollmentStatus;
import com.edusync.enrollment.repository.EnrollmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for enrollment lifecycle, extracted out of
 * EnrollmentController.
 *
 * WHY this class exists (fixes ARC-01/ARC-03/AR-03, the single most-repeated
 * finding across both audits): the previous EnrollmentController mixed HTTP
 * routing, persistence (a ConcurrentHashMap field), and business rules
 * (ownership/role checks, user-id fallback logic) in one class. This service
 * depends only on the EnrollmentRepository abstraction (constructor-injected,
 * per SOLID's Dependency Inversion Principle), which is what makes it
 * testable via EnrollmentServiceTest with a mocked repository — no Spring
 * context, no HTTP layer, no database required to test the rules below.
 *
 * A java.time.Clock is injected (rather than calling Instant.now() directly)
 * so tests can assert exact timestamps deterministically instead of using
 * fragile "within N seconds" comparisons.
 */
@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseClient courseClient;
    private final Clock clock;

    public EnrollmentService(EnrollmentRepository enrollmentRepository, CourseClient courseClient, Clock clock) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseClient = courseClient;
        this.clock = clock;
    }

    /**
     * WHY re-activating a previously DROPPED row instead of always inserting
     * a new one: the DB-level uniqueness constraint on
     * (tenant_id, course_id, user_id) in V1__create_enrollments_table.sql
     * applies to every row regardless of status (H2 2.2.x, the engine this
     * project runs on, does not support filtered/partial unique indexes —
     * verified against the installed h2 2.2.224 driver). Soft-deleting drops
     * (see Enrollment's class Javadoc for why hard delete was rejected) would
     * otherwise permanently block re-enrollment into a course a student
     * previously dropped, which is not the intended business rule — only a
     * *currently active* duplicate enrollment should be rejected. Checking
     * for an existing ENROLLED row first (application-level, in addition to
     * the DB constraint as defense in depth per DB-01) and reactivating a
     * DROPPED row when present satisfies both: it preserves the full
     * enrollment history (no row is ever deleted or duplicated) and still
     * allows a legitimate re-enrollment.
     */
    /**
     * WHY callerUserId/callerIsPrivileged were added (closes SEC-04 /
     * Critical Risk 5 in the portfolio evaluation: "any authenticated STUDENT
     * enroll a different user without consent" — enrollment-service's create
     * endpoint let any caller set an arbitrary target userId with no privilege
     * gate at all): a caller may always enroll *themselves*; enrolling a
     * *different* user is a privileged, instructor/admin-only action (e.g.
     * "add this student to my course"), mirroring the identical owner-vs-
     * privileged pattern already used by {@link #drop}.
     *
     * WHY this method also calls {@link CourseClient} (closes DB-02 /
     * Critical Risk 4: "no cross-service referential integrity... enrollments
     * can reference courses that do not exist"): a foreign key can't span two
     * separate databases in two separate microservices, so the only way to
     * enforce "this courseId is real and open for enrollment" is a real HTTP
     * call to the service that owns that data, performed here (defense in
     * depth alongside the DB-level constraints already enforced by
     * V1__create_enrollments_table.sql).
     */
    @Transactional
    public Enrollment create(String tenantId, String courseId, String targetUserId,
                              String callerUserId, boolean callerIsPrivileged) {
        if (courseId == null || courseId.isBlank()) {
            throw new BadRequestException("courseId is required");
        }
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new BadRequestException("A target user id is required (MISSING_USER_ID)");
        }
        boolean enrollingSelf = targetUserId.equals(callerUserId);
        if (!enrollingSelf && !callerIsPrivileged) {
            throw new ForbiddenException(
                    "Only an ADMIN/INSTRUCTOR may enroll a user other than themselves");
        }

        CourseSummary course = courseClient.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course '" + courseId + "' was not found"));
        if (!course.isPublished()) {
            throw new ConflictException("Course '" + courseId + "' is not open for enrollment (not published)");
        }

        boolean alreadyActive = enrollmentRepository
                .findByTenantIdAndCourseIdAndUserIdAndStatus(tenantId, courseId, targetUserId, EnrollmentStatus.ENROLLED)
                .isPresent();
        if (alreadyActive) {
            throw new ConflictException(
                    "User '" + targetUserId + "' is already enrolled in course '" + courseId + "'");
        }

        Instant now = Instant.now(clock);
        Optional<Enrollment> droppedRow = enrollmentRepository
                .findByTenantIdAndCourseIdAndUserIdAndStatus(tenantId, courseId, targetUserId, EnrollmentStatus.DROPPED);
        if (droppedRow.isPresent()) {
            Enrollment reactivated = droppedRow.get();
            reactivated.reactivate(now);
            return enrollmentRepository.save(reactivated);
        }

        Enrollment enrollment = new Enrollment(
                UUID.randomUUID().toString(), tenantId, courseId, targetUserId,
                EnrollmentStatus.ENROLLED, now, now);
        return enrollmentRepository.save(enrollment);
    }

    @Transactional(readOnly = true)
    public Page<Enrollment> listForUser(String tenantId, String userId, Pageable pageable) {
        return enrollmentRepository.findByTenantIdAndUserId(tenantId, userId, pageable);
    }

    /**
     * WHY the privilege check happens here and not in the controller: ARC-01
     * flagged authorization logic scattered across controllers with no
     * central place a reviewer could audit. The controller only computes
     * *who* the caller is (CallerContext); *whether that identity is allowed*
     * to drop a specific enrollment is a business rule that belongs with the
     * rest of the enrollment domain logic, consistent with how CourseService
     * owns course business rules.
     */
    @Transactional
    public void drop(String id, String callerUserId, boolean callerIsPrivileged) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Enrollment '" + id + "' was not found"));

        boolean isOwner = enrollment.getUserId().equals(callerUserId);
        if (!isOwner && !callerIsPrivileged) {
            throw new ForbiddenException("Only the enrollment owner or an ADMIN/INSTRUCTOR may drop this enrollment");
        }

        enrollment.drop(Instant.now(clock));
        enrollmentRepository.save(enrollment);
    }
}
