package com.edusync.submission.api;

import com.edusync.common.security.CallerContext;
import com.edusync.common.security.Role;
import com.edusync.submission.api.dto.CreateSubmissionRequestDto;
import com.edusync.submission.api.dto.SimilarityResponseDto;
import com.edusync.submission.api.dto.SubmissionResponseDto;
import com.edusync.submission.domain.Submission;
import com.edusync.submission.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * WHY this controller is now "thin" (no persistence, no business rules, no
 * plagiarism algorithm): the previous SubmissionController held a
 * ConcurrentHashMap field, a private inline `record Submission`, and the
 * full Jaccard-similarity algorithm directly. This class now only translates
 * HTTP <-> domain: parsing headers into a CallerContext where a role check
 * is required, calling SubmissionService, and mapping the returned
 * Submission/SimilarityResult to a response DTO. All authorization decisions
 * beyond "is a caller identity present" and all business/algorithm logic
 * live in SubmissionService.
 */
@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    /**
     * WHY @RequestHeader("X-User-Id") without `required = false`: Spring
     * raises MissingRequestHeaderException when it is absent, which
     * GlobalExceptionHandler already translates into a clean 400
     * MISSING_HEADER response instead of the original code's manual
     * `ResponseEntity.status(401).body(Map.of("error","UNAUTHENTICATED"))`.
     * This is an authenticity requirement only (the caller must be a real,
     * identified user) — any authenticated role may submit their own work,
     * so no role gate is applied here.
     */
    @PostMapping
    public ResponseEntity<SubmissionResponseDto> create(
            @Valid @RequestBody CreateSubmissionRequestDto request,
            @RequestHeader("X-User-Id") String userId) {
        Submission created = submissionService.create(request.assessmentId(), request.answers(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(SubmissionResponseDto.from(created));
    }

    /**
     * WHY no role gate here: this endpoint is used both by grading-service
     * (to read a submission it needs to grade) and by the student who owns
     * the submission — restricting it further would break that legitimate
     * cross-service read path without a corresponding security benefit.
     * X-User-Id is still required so an unauthenticated caller cannot read
     * submission content at all.
     */
    @GetMapping("/{id}")
    public SubmissionResponseDto get(@PathVariable String id, @RequestHeader("X-User-Id") String userId) {
        return SubmissionResponseDto.from(submissionService.getById(id));
    }

    /**
     * WHY this endpoint is restricted to INSTRUCTOR/ADMIN: this is a
     * plagiarism-detection/classmate-comparison report — a student must not
     * be able to pull a similarity report that exposes how their answers
     * compare to other students' submissions for the same assessment. The
     * original code had zero role checks anywhere; this is the one endpoint
     * in this service where "any authenticated caller" is the wrong default.
     */
    @GetMapping("/{id}/similarity")
    public SimilarityResponseDto similarity(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        CallerContext caller = CallerContext.fromHeaders(userId, null, rolesHeader, null);
        caller.requireRole(Role.INSTRUCTOR, Role.ADMIN);

        return SimilarityResponseDto.from(submissionService.computeSimilarity(id));
    }
}
