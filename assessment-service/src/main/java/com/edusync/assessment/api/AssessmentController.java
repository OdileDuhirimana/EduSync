package com.edusync.assessment.api;

import com.edusync.assessment.api.dto.AssessmentResponseDto;
import com.edusync.assessment.api.dto.AssessmentSessionResponseDto;
import com.edusync.assessment.api.dto.CreateAssessmentRequestDto;
import com.edusync.assessment.domain.Assessment;
import com.edusync.assessment.domain.AssessmentSession;
import com.edusync.assessment.domain.AssessmentType;
import com.edusync.assessment.service.AssessmentService;
import com.edusync.common.security.CallerContext;
import com.edusync.common.security.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * WHY this controller is now "thin" (no persistence, no business rules, no
 * ad hoc Map responses): the audit specifically called out that this
 * controller embedded a ConcurrentHashMap store, zero authorization, and
 * hand-rolled `Map.of("error", ...)` error bodies directly (the same
 * ARC-01/AR-03 finding course-service and enrollment-service were already
 * remediated for). This class now only translates HTTP <-> domain: parsing
 * headers into a CallerContext, calling AssessmentService, and mapping the
 * returned domain objects to response DTOs. All authorization/persistence
 * decisions live in AssessmentService and the JPA repositories
 * respectively.
 */
@RestController
@RequestMapping("/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    /**
     * WHY INSTRUCTOR/ADMIN only: assessment authoring is an instructional
     * action, mirroring CourseController#create's exact
     * `caller.requireRole(...)` pattern. The previous endpoint had no
     * authorization check whatsoever — any caller, including an
     * unauthenticated one, could create assessments.
     */
    @PostMapping
    public ResponseEntity<AssessmentResponseDto> create(
            @Valid @RequestBody CreateAssessmentRequestDto request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        CallerContext caller = CallerContext.fromHeaders(null, null, rolesHeader, null);
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);

        AssessmentType type = request.type() == null ? null : AssessmentType.valueOf(request.type());
        Assessment created = assessmentService.create(request.courseId(), request.title(), type);
        return ResponseEntity.status(HttpStatus.CREATED).body(AssessmentResponseDto.from(created));
    }

    /**
     * WHY no role restriction but a required X-User-Id: any authenticated
     * user may view an assessment they have access to, so there is no role
     * check here — but the caller must still be identified, so
     * `required = false` is deliberately removed. Spring raises
     * MissingRequestHeaderException when the header is absent, which
     * GlobalExceptionHandler already translates into a 400 MISSING_HEADER
     * response (same pattern as EnrollmentController#myEnrollments).
     */
    @GetMapping("/{id}")
    public AssessmentResponseDto get(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        return AssessmentResponseDto.from(assessmentService.getById(id));
    }

    /**
     * WHY the session is recorded against the X-User-Id header rather than
     * any client-supplied body field: the header is the identity the
     * gateway has already verified from the caller's JWT, whereas anything
     * in the request body is fully client-controlled. Accepting a
     * body-supplied user id here would let a caller start (and thus
     * "verify") a timed session on another student's behalf. No specific
     * role is required beyond being identified — both STUDENT and higher
     * roles may start an assessment session.
     */
    @PostMapping("/{id}/start")
    public AssessmentSessionResponseDto start(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        AssessmentSession session = assessmentService.start(id, userId);
        return AssessmentSessionResponseDto.from(session);
    }
}
