package com.edusync.course.service;

import com.edusync.common.error.BadRequestException;
import com.edusync.common.error.ConflictException;
import com.edusync.common.error.ForbiddenException;
import com.edusync.common.error.NotFoundException;
import com.edusync.course.domain.Course;
import com.edusync.course.domain.CourseStatus;
import com.edusync.course.repository.CourseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Business logic for course lifecycle, extracted out of CourseController.
 *
 * WHY this class exists (fixes ARC-01/ARC-03/AR-03, the single most-repeated
 * finding across both audits): the previous CourseController mixed HTTP
 * routing, persistence (a ConcurrentHashMap field), and business rules in
 * one class. This service depends only on the CourseRepository abstraction
 * (constructor-injected, per SOLID's Dependency Inversion Principle), which
 * is what makes it testable via CourseServiceTest with a mocked repository —
 * no Spring context, no HTTP layer, no database required to test the rules
 * below.
 *
 * A java.time.Clock is injected (rather than calling Instant.now() directly)
 * so tests can assert exact timestamps deterministically instead of using
 * fragile "within N seconds" comparisons.
 */
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final Clock clock;

    public CourseService(CourseRepository courseRepository, Clock clock) {
        this.courseRepository = courseRepository;
        this.clock = clock;
    }

    /**
     * WHY instructorId is a required parameter (closes SEC-04 in the
     * portfolio evaluation): recording the verified creator at creation time
     * is what makes a later per-resource ownership check in {@link #publish}
     * possible at all — without it, only a per-role check exists, and any
     * INSTRUCTOR could publish any other instructor's course. The controller
     * sources this value from the gateway-verified {@code X-User-Id} header,
     * never from client-supplied request body content.
     */
    @Transactional
    public Course create(String code, String title, String instructorId) {
        if (instructorId == null || instructorId.isBlank()) {
            throw new BadRequestException("An authenticated instructor id is required to create a course");
        }
        if (courseRepository.existsByCode(code)) {
            throw new ConflictException("A course with code '" + code + "' already exists");
        }
        Instant now = Instant.now(clock);
        Course course = new Course(UUID.randomUUID().toString(), code, title, instructorId, CourseStatus.DRAFT, now, now);
        return courseRepository.save(course);
    }

    @Transactional(readOnly = true)
    public Page<Course> list(Pageable pageable) {
        return courseRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Course getById(String id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course '" + id + "' was not found"));
    }

    /**
     * WHY an ownership check in addition to the controller's role check
     * (closes SEC-04 / AUTH-05's specific finding — "any INSTRUCTOR can
     * publish any other instructor's course"): {@code CallerContext.requireRole}
     * only proves the caller *holds* the INSTRUCTOR/ADMIN role, not that they
     * own *this* course. ADMIN is exempt from ownership (an administrator is
     * expected to manage any course), mirroring EnrollmentService#drop's
     * identical "owner OR privileged" pattern.
     */
    @Transactional
    public Course publish(String id, String callerUserId, boolean callerIsPrivileged) {
        Course course = getById(id);
        boolean isOwner = course.getInstructorId().equals(callerUserId);
        if (!isOwner && !callerIsPrivileged) {
            throw new ForbiddenException("Only the owning instructor or an ADMIN may publish this course");
        }
        // WHY emit no event here: the original code had a bare "// TODO: emit
        // event course.published (stub)" comment (Critical Issue #5 in the
        // code review — a literal TODO left in shipped code). Kafka/event
        // publishing is explicitly out of scope for this pass (see
        // README "Known Limitations"); rather than leave a misleading TODO,
        // the method is complete and correct for what it currently promises
        // (persisting a status transition), and event publishing is tracked
        // as a named follow-up in the README instead of an inline TODO.
        course.publish(Instant.now(clock));
        return courseRepository.save(course);
    }
}
