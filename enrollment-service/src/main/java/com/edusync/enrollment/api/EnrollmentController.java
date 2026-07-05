package com.edusync.enrollment.api;

import com.edusync.common.dto.PageResponse;
import com.edusync.common.security.CallerContext;
import com.edusync.common.security.Role;
import com.edusync.enrollment.api.dto.CreateEnrollmentRequestDto;
import com.edusync.enrollment.api.dto.EnrollmentResponseDto;
import com.edusync.enrollment.domain.Enrollment;
import com.edusync.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * now only translates HTTP <-> domain: parsing headers into a CallerContext
 * (or the target-user fallback logic below), calling EnrollmentService, and
 * mapping the returned Enrollment entity to a response DTO. All
 * authorization/persistence decisions live in EnrollmentService and the JPA
 * repository respectively.
 */
@RestController
@RequestMapping("/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    /**
     * WHY the target-user fallback logic stays in the controller rather than
     * moving into EnrollmentService: resolving "who is this enrollment for"
     * from the request body vs. the caller's own identity header is an HTTP
     * concern (which header/body field wins), not a business rule — the
     * business rules ("the resolved target user id must be non-blank", and
     * now "only a privileged caller may enroll someone other than
     * themselves") are still enforced by EnrollmentService#create so they
     * can't be bypassed by a future second caller of the service.
     */
    @PostMapping
    public ResponseEntity<EnrollmentResponseDto> create(
            @Valid @RequestBody CreateEnrollmentRequestDto request,
            @RequestHeader(value = "X-User-Id", required = false) String callerUserId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader) {
        CallerContext caller = CallerContext.fromHeaders(callerUserId, null, rolesHeader, tenantIdHeader);
        String targetUserId = request.userId() != null && !request.userId().isBlank()
                ? request.userId()
                : caller.userId();
        boolean callerIsPrivileged = caller.hasAnyRole(Role.ADMIN, Role.INSTRUCTOR);

        Enrollment created = enrollmentService.create(
                caller.tenantId(), request.courseId(), targetUserId, caller.userId(), callerIsPrivileged);
        return ResponseEntity.status(HttpStatus.CREATED).body(EnrollmentResponseDto.from(created));
    }

    /**
     * WHY paginated (closes API-05/06/07 from the audits): the previous
     * endpoint returned the entire unbounded in-memory collection, filtered
     * in Java, on every call. Defaults to 20 items/page, sorted by
     * createdAt descending so a student's most recent enrollments surface
     * first — the same default ordering rationale as CourseController#list.
     *
     * WHY @RequestHeader("X-User-Id") without `required = false`: Spring
     * raises MissingRequestHeaderException when it is absent, which
     * GlobalExceptionHandler already translates into a 400 MISSING_HEADER
     * response — no manual null-check needed here.
     */
    @GetMapping("/me")
    public PageResponse<EnrollmentResponseDto> myEnrollments(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        CallerContext caller = CallerContext.fromHeaders(userId, null, null, tenantIdHeader);
        Page<EnrollmentResponseDto> page = enrollmentService
                .listForUser(caller.tenantId(), caller.userId(), pageable)
                .map(EnrollmentResponseDto::from);
        return PageResponse.from(page);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> drop(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        CallerContext caller = CallerContext.fromHeaders(userId, null, rolesHeader, null);
        boolean callerIsPrivileged = caller.hasAnyRole(Role.ADMIN, Role.INSTRUCTOR);

        enrollmentService.drop(id, caller.userId(), callerIsPrivileged);
        return ResponseEntity.noContent().build();
    }
}
