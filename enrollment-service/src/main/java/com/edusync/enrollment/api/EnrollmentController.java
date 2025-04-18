package com.edusync.enrollment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/enrollments")
public class EnrollmentController {

    private final Map<String, Enrollment> store = new ConcurrentHashMap<>();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    public record CreateEnrollmentRequest(@NotBlank String courseId, String userId) {}

    public record Enrollment(String id, String tenantId, String courseId, String userId, String status, Instant createdAt) {}

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateEnrollmentRequest req,
                                    @RequestHeader(value = "X-User-Id", required = false) String callerUserId,
                                    @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
                                    @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        String targetUserId = req.userId() != null && !req.userId().isBlank() ? req.userId() : callerUserId;
        if (targetUserId == null || targetUserId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "MISSING_USER_ID"));
        }
        String id = UUID.randomUUID().toString();
        Enrollment e = new Enrollment(id, tenantIdOrDefault(tenantId), req.courseId(), targetUserId, "ENROLLED", Instant.now());
        store.put(id, e);
        return ResponseEntity.status(HttpStatus.CREATED).body(e);
    }

    @GetMapping("/me")
    public ResponseEntity<?> myEnrollments(@RequestHeader("X-User-Id") String userId,
                                           @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        List<Enrollment> list = store.values().stream()
                .filter(e -> Objects.equals(e.userId(), userId) && Objects.equals(e.tenantId(), tenantIdOrDefault(tenantId)))
                .toList();
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> drop(@PathVariable String id,
                                  @RequestHeader("X-User-Id") String userId,
                                  @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader) {
        Enrollment e = store.get(id);
        if (e == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
        boolean isAdminOrInstructor = rolesHeader != null && (rolesHeader.contains("ADMIN") || rolesHeader.contains("INSTRUCTOR"));
        if (!Objects.equals(e.userId(), userId) && !isAdminOrInstructor) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "FORBIDDEN"));
        }
        store.remove(id);
        return ResponseEntity.noContent().build();
    }

    private String tenantIdOrDefault(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }
}
