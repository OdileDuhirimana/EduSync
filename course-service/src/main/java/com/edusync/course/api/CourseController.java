package com.edusync.course.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final Map<String, Course> store = new ConcurrentHashMap<>();

    record Course(String id, String code, String title, String status, Instant createdAt, Instant updatedAt) {}

    public record CreateCourseRequest(@NotBlank String code, @NotBlank String title) {}

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateCourseRequest req,
                                    @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        if (roles == null || !roles.contains("INSTRUCTOR")) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN", "message", "INSTRUCTOR role required"));
        }
        String id = UUID.randomUUID().toString();
        Course course = new Course(id, req.code(), req.title(), "DRAFT", Instant.now(), Instant.now());
        store.put(id, course);
        return ResponseEntity.status(HttpStatus.CREATED).body(course);
    }

    @GetMapping
    public List<Course> list() {
        return new ArrayList<>(store.values());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        Course c = store.get(id);
        if (c == null) return ResponseEntity.status(404).body(Map.of("error","NOT_FOUND"));
        return ResponseEntity.ok(c);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable String id,
                                     @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        if (roles == null || !roles.contains("INSTRUCTOR")) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN", "message", "INSTRUCTOR role required"));
        }
        Course c = store.get(id);
        if (c == null) return ResponseEntity.status(404).body(Map.of("error","NOT_FOUND"));
        Course updated = new Course(c.id(), c.code(), c.title(), "PUBLISHED", c.createdAt(), Instant.now());
        store.put(id, updated);
        // TODO: emit event course.published (stub)
        return ResponseEntity.ok(updated);
    }
}
