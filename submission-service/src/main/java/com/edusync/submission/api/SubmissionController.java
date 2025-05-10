package com.edusync.submission.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final Map<String, Submission> store = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    public record CreateSubmissionRequest(@NotBlank String assessmentId, List<Map<String, Object>> answers) {}

    private record Submission(
            String id,
            String assessmentId,
            String userId,
            List<Map<String, Object>> answers,
            String status,
            String createdAt,
            String normalizedAnswerText
    ) {
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateSubmissionRequest req,
                                    @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error","UNAUTHENTICATED"));
        String id = UUID.randomUUID().toString();
        List<Map<String, Object>> answers = req.answers() == null ? List.of() : req.answers();
        Submission s = new Submission(
                id,
                req.assessmentId(),
                userId,
                answers,
                "SUBMITTED",
                Instant.now().toString(),
                normalize(extractText(answers))
        );
        store.put(id, s);
        return ResponseEntity.status(HttpStatus.CREATED).body(toApiResponse(s));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        Submission s = store.get(id);
        if (s == null) return ResponseEntity.status(404).body(Map.of("error","NOT_FOUND"));
        return ResponseEntity.ok(toApiResponse(s));
    }

    @GetMapping("/{id}/similarity")
    public ResponseEntity<?> similarity(@PathVariable String id) {
        Submission target = store.get(id);
        if (target == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
        }

        List<Submission> candidates = store.values().stream()
                .filter(s -> !Objects.equals(s.id(), target.id()))
                .filter(s -> Objects.equals(s.assessmentId(), target.assessmentId()))
                .toList();

        List<Map<String, Object>> matches = candidates.stream()
                .map(candidate -> {
                    double score = jaccard(target.normalizedAnswerText(), candidate.normalizedAnswerText());
                    Map<String, Object> match = new HashMap<>();
                    match.put("submissionId", candidate.id());
                    match.put("userId", candidate.userId());
                    match.put("similarityScore", round2(score));
                    return match;
                })
                .sorted((a, b) -> Double.compare((double) b.get("similarityScore"), (double) a.get("similarityScore")))
                .limit(5)
                .collect(Collectors.toList());

        double maxSimilarity = matches.isEmpty() ? 0.0 : (double) matches.get(0).get("similarityScore");
        String riskLevel = maxSimilarity >= 0.75 ? "HIGH" : maxSimilarity >= 0.45 ? "MEDIUM" : "LOW";

        return ResponseEntity.ok(Map.of(
                "submissionId", target.id(),
                "assessmentId", target.assessmentId(),
                "riskLevel", riskLevel,
                "maxSimilarity", round2(maxSimilarity),
                "comparedSubmissions", candidates.size(),
                "matches", matches
        ));
    }

    private Map<String, Object> toApiResponse(Submission s) {
        return Map.of(
                "id", s.id(),
                "assessmentId", s.assessmentId(),
                "userId", s.userId(),
                "answers", s.answers(),
                "status", s.status(),
                "createdAt", s.createdAt()
        );
    }

    private String extractText(List<Map<String, Object>> answers) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> answer : answers) {
            appendValue(sb, answer);
            sb.append(' ');
        }
        return sb.toString();
    }

    private void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            sb.append(text).append(' ');
            return;
        }
        if (value instanceof Number number) {
            sb.append(number).append(' ');
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object nested : map.values()) {
                appendValue(sb, nested);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object nested : collection) {
                appendValue(sb, nested);
            }
            return;
        }
        sb.append(String.valueOf(value)).append(' ');
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double jaccard(String left, String right) {
        if (left.isBlank() || right.isBlank()) {
            return 0.0;
        }
        Set<String> leftSet = Arrays.stream(left.split(" "))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
        Set<String> rightSet = Arrays.stream(right.split(" "))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
        if (leftSet.isEmpty() || rightSet.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(leftSet);
        intersection.retainAll(rightSet);
        Set<String> union = new HashSet<>(leftSet);
        union.addAll(rightSet);
        if (union.isEmpty()) {
            return 0.0;
        }
        return (double) intersection.size() / union.size();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
