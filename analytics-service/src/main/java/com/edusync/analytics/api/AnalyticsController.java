package com.edusync.analytics.api;

import com.edusync.analytics.api.dto.AtRiskRequestDto;
import com.edusync.analytics.api.dto.GradeForecastRequestDto;
import com.edusync.analytics.api.dto.RecordEngagementEventRequestDto;
import com.edusync.analytics.api.dto.RecordFunnelEventRequestDto;
import com.edusync.analytics.api.dto.RecordGradeSnapshotRequestDto;
import com.edusync.analytics.api.dto.StudyPlanRequestDto;
import com.edusync.analytics.domain.EngagementEvent;
import com.edusync.analytics.domain.FunnelEvent;
import com.edusync.analytics.domain.GradeRecordSnapshot;
import com.edusync.analytics.service.AnalyticsService;
import com.edusync.common.error.BadRequestException;
import com.edusync.common.security.CallerContext;
import com.edusync.common.security.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * WHY this controller is now "thin" (no persistence, no algorithm code, no
 * static fixtures pretending to be analytics): the pre-remediation version
 * held both fully-mocked aggregate endpoints (the code review's Critical
 * Issue: "fixtures masquerading as computed responses") and real, correct
 * algorithms implemented directly on the controller with ad hoc
 * null-checking instead of Bean Validation. Both problems are fixed the same
 * way CourseController/EnrollmentController were fixed in the sibling
 * services: this class only translates HTTP <-> AnalyticsService calls —
 * parsing headers into authorization decisions and delegating everything
 * else.
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    // ---- Part A: ingestion + real aggregation ------------------------------
    //
    // WHY ingestion and read endpoints both require INSTRUCTOR/ADMIN: raw
    // engagement/grade/funnel events and the aggregates computed from them
    // are instructor-facing course insights, not something an individual
    // student is entitled to see or write (a student should not be able to
    // fabricate their own engagement history via the ingestion endpoints).

    @PostMapping("/events/engagement")
    public ResponseEntity<Map<String, Object>> recordEngagementEvent(
            @Valid @RequestBody RecordEngagementEventRequestDto request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        requireInstructorOrAdmin(rolesHeader);
        EngagementEvent saved = analyticsService.recordEngagementEvent(
                request.courseId(), request.userId(), request.eventType());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "recorded", true));
    }

    @PostMapping("/events/grade")
    public ResponseEntity<Map<String, Object>> recordGradeSnapshot(
            @Valid @RequestBody RecordGradeSnapshotRequestDto request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        requireInstructorOrAdmin(rolesHeader);
        GradeRecordSnapshot saved = analyticsService.recordGradeSnapshot(
                request.courseId(), request.userId(), request.letterGrade());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "recorded", true));
    }

    @PostMapping("/events/funnel")
    public ResponseEntity<Map<String, Object>> recordFunnelEvent(
            @Valid @RequestBody RecordFunnelEventRequestDto request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        requireInstructorOrAdmin(rolesHeader);
        FunnelEvent saved = analyticsService.recordFunnelEvent(request.courseId(), request.stage());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId(), "recorded", true));
    }

    @GetMapping("/engagement")
    public Map<String, Object> engagement(
            @RequestParam String courseId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        requireInstructorOrAdmin(rolesHeader);
        return analyticsService.engagement(courseId);
    }

    @GetMapping("/grade-distribution")
    public Map<String, Object> gradeDistribution(
            @RequestParam String courseId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        requireInstructorOrAdmin(rolesHeader);
        return analyticsService.gradeDistribution(courseId);
    }

    @GetMapping("/funnels")
    public Map<String, Object> funnels(
            @RequestParam String courseId,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        requireInstructorOrAdmin(rolesHeader);
        return analyticsService.funnels(courseId);
    }

    // ---- Part B: self-service planning tools --------------------------------

    /**
     * WHY only a valid X-User-Id header (no role check): a study plan is a
     * student planning their own studying — legitimately self-service,
     * unlike /at-risk below which operates on a batch of other learners'
     * data.
     */
    @PostMapping("/study-plan")
    public Map<String, Object> studyPlan(
            @Valid @RequestBody StudyPlanRequestDto request,
            @RequestHeader("X-User-Id") String userId) {
        requireNonBlankCaller(userId);
        return analyticsService.studyPlan(request);
    }

    /**
     * WHY INSTRUCTOR/ADMIN only: this endpoint scores a batch of other
     * learners' signals at once (cross-student data), which is instructor
     * oversight, not student self-service.
     */
    @PostMapping("/at-risk")
    public Map<String, Object> atRisk(
            @Valid @RequestBody AtRiskRequestDto request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        requireInstructorOrAdmin(rolesHeader);
        return analyticsService.atRisk(request);
    }

    /** WHY only a valid X-User-Id header: same self-service rationale as /study-plan. */
    @PostMapping("/grade-forecast")
    public Map<String, Object> gradeForecast(
            @Valid @RequestBody GradeForecastRequestDto request,
            @RequestHeader("X-User-Id") String userId) {
        requireNonBlankCaller(userId);
        return analyticsService.gradeForecast(request);
    }

    private void requireInstructorOrAdmin(String rolesHeader) {
        CallerContext.fromHeaders(null, null, rolesHeader, null).requireRole(Role.INSTRUCTOR, Role.ADMIN);
    }

    /**
     * WHY this check lives in the controller rather than CallerContext: it
     * is purely "is the required header non-blank", not a role/permission
     * decision. {@code @RequestHeader("X-User-Id")} already guarantees the
     * header is present — Spring throws MissingRequestHeaderException,
     * mapped to a 400 by GlobalExceptionHandler, when it is absent entirely —
     * this only guards against a caller sending an empty string.
     */
    private void requireNonBlankCaller(String userId) {
        if (userId.isBlank()) {
            throw new BadRequestException("X-User-Id header must not be blank");
        }
    }
}
