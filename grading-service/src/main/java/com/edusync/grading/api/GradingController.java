package com.edusync.grading.api;

import com.edusync.common.security.CallerContext;
import com.edusync.common.security.Role;
import com.edusync.grading.api.dto.AutoGradeRequestDto;
import com.edusync.grading.api.dto.GradeResponseDto;
import com.edusync.grading.api.dto.ManualGradeRequestDto;
import com.edusync.grading.api.dto.RegradeDecisionRequestDto;
import com.edusync.grading.api.dto.RegradeRequestDto;
import com.edusync.grading.api.dto.RegradeResponseDto;
import com.edusync.grading.domain.GradeRecord;
import com.edusync.grading.domain.RegradeCase;
import com.edusync.grading.service.GradingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * WHY this controller is now "thin" (no persistence, no business rules): this
 * mirrors CourseController/EnrollmentController exactly — ARC-01/AR-03 in the
 * audits flagged that controllers embedded a ConcurrentHashMap store and
 * business logic directly. This class only translates HTTP <-> domain:
 * parsing headers into a CallerContext, calling GradingService, and mapping
 * the returned entities to response DTOs. All authorization/persistence
 * decisions live in GradingService and the JPA repositories respectively.
 */
@RestController
@RequestMapping("/grading")
public class GradingController {

    private final GradingService gradingService;

    public GradingController(GradingService gradingService) {
        this.gradingService = gradingService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    /**
     * WHY INSTRUCTOR/ADMIN only: grading (even automated grading) is an
     * instructor action that determines a student's recorded score — the
     * audits' single most-cited critical risk was "6 of 9 services have zero
     * role checks," with grading actions called out specifically as the most
     * sensitive gap.
     */
    @PostMapping("/auto/{submissionId}")
    public GradeResponseDto autoGrade(
            @PathVariable String submissionId,
            @Valid @RequestBody AutoGradeRequestDto request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        CallerContext caller = CallerContext.fromHeaders(null, null, rolesHeader, null);
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);

        GradeRecord grade = gradingService.autoGrade(submissionId, request.answers(), request.answerKey());
        return GradeResponseDto.from(grade);
    }

    /** WHY INSTRUCTOR/ADMIN only: see {@link #autoGrade} — the same grading-authority rationale applies. */
    @PostMapping("/manual/{submissionId}")
    public GradeResponseDto manualGrade(
            @PathVariable String submissionId,
            @Valid @RequestBody ManualGradeRequestDto request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        CallerContext caller = CallerContext.fromHeaders(null, null, rolesHeader, null);
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);

        GradeRecord grade = gradingService.manualGrade(submissionId, request.breakdown(), request.feedback());
        return GradeResponseDto.from(grade);
    }

    /** WHY INSTRUCTOR/ADMIN only: publishing a grade to a student is an instructor action, same as grading itself. */
    @PostMapping("/{submissionId}/publish")
    public GradeResponseDto publish(
            @PathVariable String submissionId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        CallerContext caller = CallerContext.fromHeaders(null, null, rolesHeader, null);
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);

        GradeRecord grade = gradingService.publish(submissionId);
        return GradeResponseDto.from(grade);
    }

    /**
     * WHY any authenticated caller (STUDENT included) may call this: a
     * student requesting review of their own submission's grade is the
     * intended use of this endpoint (see GradingService#requestRegrade's
     * Javadoc). Only a non-blank identity is required, not a specific role.
     *
     * WHY {@code X-User-Id} is required (no {@code required = false}): Spring
     * raises MissingRequestHeaderException when it is absent, which
     * GlobalExceptionHandler already translates into a clean 400
     * MISSING_HEADER response. {@code requestedBy} is deliberately absent
     * from {@link RegradeRequestDto} — see that class's Javadoc for the
     * spoofing gap this closes: the requester's identity comes only from this
     * verified header, never from client-supplied body content.
     */
    @PostMapping("/regrade/{submissionId}/request")
    public ResponseEntity<RegradeResponseDto> requestRegrade(
            @PathVariable String submissionId,
            @Valid @RequestBody RegradeRequestDto request,
            @RequestHeader("X-User-Id") String userId) {
        RegradeCase regradeCase = gradingService.requestRegrade(submissionId, userId, request.reason());
        GradeRecord grade = gradingService.getGradeBySubmissionId(regradeCase.getSubmissionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegradeResponseDto.from(regradeCase, grade));
    }

    /**
     * WHY INSTRUCTOR/ADMIN only, and why {@code X-User-Id} is required here
     * too: deciding a regrade case is the most sensitive action in this
     * service — it can change a student's recorded score. The audits singled
     * out grade override/regrade decisions as the single most sensitive
     * authorization gap. {@code moderatorId} is deliberately absent from
     * {@link RegradeDecisionRequestDto} for the same spoofing-prevention
     * reason as {@code requestedBy} above — see that class's Javadoc.
     */
    @PostMapping("/regrade/{requestId}/decision")
    public RegradeResponseDto decideRegrade(
            @PathVariable String requestId,
            @Valid @RequestBody RegradeDecisionRequestDto request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestHeader("X-User-Id") String moderatorId) {
        CallerContext caller = CallerContext.fromHeaders(moderatorId, null, rolesHeader, null);
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);

        RegradeCase decided = gradingService.decideRegrade(
                requestId, moderatorId, request.decision(), request.note(), request.overrideTotal());
        GradeRecord grade = gradingService.getGradeBySubmissionId(decided.getSubmissionId());
        return RegradeResponseDto.from(decided, grade);
    }

    @GetMapping("/regrade/{requestId}")
    public RegradeResponseDto getRegrade(@PathVariable String requestId) {
        RegradeCase regradeCase = gradingService.getRegradeCase(requestId);
        GradeRecord grade = gradingService.getGradeBySubmissionId(regradeCase.getSubmissionId());
        return RegradeResponseDto.from(regradeCase, grade);
    }
}
