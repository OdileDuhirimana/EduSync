package com.edusync.assessment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/assessments")
public class AssessmentController {

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    public record CreateAssessmentRequest(@NotBlank String courseId, @NotBlank String title, String type) {}

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateAssessmentRequest req) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> a = new HashMap<>();
        a.put("id", id);
        a.put("courseId", req.courseId());
        a.put("title", req.title());
        a.put("type", req.type() == null ? "QUIZ" : req.type());
        a.put("createdAt", Instant.now().toString());
        store.put(id, a);
        return ResponseEntity.status(HttpStatus.CREATED).body(a);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        Map<String, Object> a = store.get(id);
        if (a == null) return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        return ResponseEntity.ok(a);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(@PathVariable String id) {
        Map<String, Object> a = store.get(id);
        if (a == null) return ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        Map<String, Object> body = new HashMap<>();
        body.put("assessmentId", id);
        body.put("window", Map.of("timeLimitMin", 30));
        body.put("token", "ast-" + UUID.randomUUID());
        return ResponseEntity.ok(body);
    }
}
