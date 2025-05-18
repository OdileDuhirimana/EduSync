package com.edusync.grading.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/grading")
public class GradingController {

    private final Map<String, GradeRecord> grades = new ConcurrentHashMap<>();
    private final Map<String, RegradeCase> regrades = new ConcurrentHashMap<>();

    private record GradeRecord(
            String submissionId,
            Map<String, Object> breakdown,
            int total,
            String feedback,
            String status,
            String updatedAt
    ) {
    }

    private record RegradeCase(
            String requestId,
            String submissionId,
            String requestedBy,
            String reason,
            String status,
            String requestedAt,
            String decidedBy,
            String decisionNote,
            String decidedAt,
            Integer overrideTotal
    ) {
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    public record ManualGradeRequest(@NotNull Map<String, Object> breakdown, String feedback) {}
    public record RegradeRequestBody(@NotBlank String requestedBy, @NotBlank String reason) {}
    public record RegradeDecisionBody(@NotBlank String moderatorId, @NotBlank String decision, String note, Integer overrideTotal) {}

    @PostMapping("/auto/{submissionId}")
    public ResponseEntity<?> auto(@PathVariable String submissionId) {
        int total = 50 + Math.abs(submissionId.hashCode() % 51);
        GradeRecord record = new GradeRecord(
                submissionId,
                Map.of("auto", total),
                total,
                "Auto-graded result",
                "GRADED",
                Instant.now().toString()
        );
        grades.put(submissionId, record);

        Map<String, Object> res = new HashMap<>();
        res.put("submissionId", submissionId);
        res.put("score", Map.of("total", total, "breakdown", record.breakdown()));
        res.put("status", "GRADED");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/manual/{submissionId}")
    public ResponseEntity<?> manual(@PathVariable String submissionId, @Valid @RequestBody ManualGradeRequest req) {
        int total = req.breakdown().values().stream()
                .mapToInt(value -> value instanceof Number n ? n.intValue() : 0)
                .sum();
        GradeRecord record = new GradeRecord(
                submissionId,
                req.breakdown(),
                total,
                req.feedback(),
                "GRADED",
                Instant.now().toString()
        );
        grades.put(submissionId, record);

        Map<String, Object> res = new HashMap<>();
        res.put("submissionId", submissionId);
        res.put("score", Map.of("total", total, "breakdown", req.breakdown()));
        res.put("feedback", req.feedback());
        res.put("status", "GRADED");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{submissionId}/publish")
    public ResponseEntity<?> publish(@PathVariable String submissionId) {
        GradeRecord grade = grades.get(submissionId);
        if (grade == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_GRADED"));
        }
        Map<String, Object> res = new HashMap<>();
        res.put("submissionId", submissionId);
        res.put("score", Map.of("total", grade.total(), "breakdown", grade.breakdown()));
        res.put("event", "GRADE_PUBLISHED");
        res.put("publishedAt", Instant.now().toString());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/regrade/{submissionId}/request")
    public ResponseEntity<?> requestRegrade(@PathVariable String submissionId, @Valid @RequestBody RegradeRequestBody req) {
        GradeRecord grade = grades.get(submissionId);
        if (grade == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_GRADED"));
        }

        String requestId = "rg-" + UUID.randomUUID();
        RegradeCase regrade = new RegradeCase(
                requestId,
                submissionId,
                req.requestedBy(),
                req.reason(),
                "PENDING",
                Instant.now().toString(),
                null,
                null,
                null,
                null
        );
        regrades.put(requestId, regrade);

        return ResponseEntity.status(HttpStatus.CREATED).body(toApiRegrade(regrade, grade));
    }

    @PostMapping("/regrade/{requestId}/decision")
    public ResponseEntity<?> decideRegrade(@PathVariable String requestId, @Valid @RequestBody RegradeDecisionBody req) {
        RegradeCase current = regrades.get(requestId);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
        }
        if (!"PENDING".equals(current.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "ALREADY_DECIDED"));
        }

        String normalizedDecision = req.decision().trim().toUpperCase();
        if (!"APPROVE".equals(normalizedDecision) && !"REJECT".equals(normalizedDecision)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "INVALID_DECISION"));
        }

        GradeRecord grade = grades.get(current.submissionId());
        if (grade == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_GRADED"));
        }

        GradeRecord updatedGrade = grade;
        Integer overrideTotal = null;
        if ("APPROVE".equals(normalizedDecision) && req.overrideTotal() != null) {
            int normalizedTotal = Math.max(0, Math.min(100, req.overrideTotal()));
            overrideTotal = normalizedTotal;
            updatedGrade = new GradeRecord(
                    grade.submissionId(),
                    grade.breakdown(),
                    normalizedTotal,
                    grade.feedback(),
                    "GRADED_OVERRIDDEN",
                    Instant.now().toString()
            );
            grades.put(grade.submissionId(), updatedGrade);
        }

        RegradeCase decided = new RegradeCase(
                current.requestId(),
                current.submissionId(),
                current.requestedBy(),
                current.reason(),
                "APPROVE".equals(normalizedDecision) ? "APPROVED" : "REJECTED",
                current.requestedAt(),
                req.moderatorId(),
                req.note(),
                Instant.now().toString(),
                overrideTotal
        );
        regrades.put(requestId, decided);

        return ResponseEntity.ok(toApiRegrade(decided, updatedGrade));
    }

    @GetMapping("/regrade/{requestId}")
    public ResponseEntity<?> getRegrade(@PathVariable String requestId) {
        RegradeCase regrade = regrades.get(requestId);
        if (regrade == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
        }
        GradeRecord grade = grades.get(regrade.submissionId());
        return ResponseEntity.ok(toApiRegrade(regrade, grade));
    }

    private Map<String, Object> toApiRegrade(RegradeCase regrade, GradeRecord grade) {
        Map<String, Object> response = new HashMap<>();
        response.put("requestId", regrade.requestId());
        response.put("submissionId", regrade.submissionId());
        response.put("status", regrade.status());
        response.put("requestedBy", regrade.requestedBy());
        response.put("reason", regrade.reason());
        response.put("requestedAt", regrade.requestedAt());
        response.put("decidedBy", regrade.decidedBy());
        response.put("decisionNote", regrade.decisionNote());
        response.put("decidedAt", regrade.decidedAt());
        response.put("overrideTotal", regrade.overrideTotal());
        if (grade != null) {
            response.put("currentGrade", Map.of(
                    "total", grade.total(),
                    "status", grade.status(),
                    "updatedAt", grade.updatedAt()
            ));
        }
        return response;
    }
}
