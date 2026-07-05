package com.edusync.course.api;

import com.edusync.common.dto.PageResponse;
import com.edusync.common.security.CallerContext;
import com.edusync.common.security.Role;
import com.edusync.course.api.dto.CourseResponseDto;
import com.edusync.course.api.dto.CreateCourseRequestDto;
import com.edusync.course.domain.Course;
import com.edusync.course.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * WHY this controller is now "thin" (no persistence, no business rules):
 * ARC-01/AR-03 in the audits specifically called out that controllers
 * embedded a ConcurrentHashMap store and business logic directly. This class
 * now only translates HTTP <-> domain: parsing headers into a CallerContext,
 * calling CourseService, and mapping the returned Course entity to a
 * response DTO. All authorization/persistence decisions live in CourseService
 * and the JPA repository respectively.
 */
@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    @PostMapping
    public ResponseEntity<CourseResponseDto> create(
            @Valid @RequestBody CreateCourseRequestDto request,
            @RequestHeader(value = "X-User-Id", required = false) String callerUserId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        CallerContext caller = CallerContext.fromHeaders(callerUserId, null, rolesHeader, null);
        // WHY the role check runs before CourseService even sees the missing-id
        // case: a caller with no INSTRUCTOR/ADMIN role at all must always see a
        // 403 regardless of what other headers they did or didn't send — this
        // keeps createRequiresInstructorRole/studentCannotCreateCourse's
        // "wrong or missing role -> 403" contract independent of X-User-Id
        // presence. CourseService.create still independently validates
        // instructorId is non-blank as defense in depth (DB-01 principle: never
        // rely on a single layer for an integrity-critical check).
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);

        Course created = courseService.create(request.code(), request.title(), caller.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CourseResponseDto.from(created));
    }

    /**
     * WHY paginated (closes API-05/06/07 and API-03/04/05 from the audits):
     * the previous endpoint returned the entire unbounded in-memory
     * collection on every call. Defaults to 20 items/page, capped implicitly
     * by Pageable's own size; sorting defaults to createdAt descending so the
     * most recently created courses surface first, a sensible default for a
     * course catalog.
     */
    @GetMapping
    public PageResponse<CourseResponseDto> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        Page<CourseResponseDto> page = courseService.list(pageable).map(CourseResponseDto::from);
        return PageResponse.from(page);
    }

    @GetMapping("/{id}")
    public CourseResponseDto get(@PathVariable String id) {
        return CourseResponseDto.from(courseService.getById(id));
    }

    @PostMapping("/{id}/publish")
    public CourseResponseDto publish(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String callerUserId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        CallerContext caller = CallerContext.fromHeaders(callerUserId, null, rolesHeader, null);
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);
        // WHY ADMIN-only (not "any privileged role") bypasses ownership: the
        // role check above already lets both INSTRUCTOR and ADMIN through the
        // gate, but SEC-04 specifically flagged that ANY instructor could
        // publish ANY course. A non-owning INSTRUCTOR must still be rejected by
        // CourseService#publish's ownership check; only ADMIN is exempt from it
        // (an administrator is expected to manage any course).
        boolean callerIsPrivileged = caller.hasRole(Role.ADMIN);

        return CourseResponseDto.from(courseService.publish(id, caller.userId(), callerIsPrivileged));
    }
}
