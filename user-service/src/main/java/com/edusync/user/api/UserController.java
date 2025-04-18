package com.edusync.user.api;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                @RequestHeader(value = "X-User-Email", required = false) String email) {
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        }
        Map<String, Object> me = new HashMap<>();
        me.put("id", userId);
        me.put("email", email);
        me.put("firstName", "Demo");
        me.put("lastName", "User");
        return ResponseEntity.ok(me);
    }

    public record UpdateProfileRequest(@NotBlank String firstName, @NotBlank String lastName) {}

    @PatchMapping("/me")
    public ResponseEntity<?> updateMe(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                      @RequestBody UpdateProfileRequest req) {
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        }
        Map<String, Object> me = new HashMap<>();
        me.put("id", userId);
        me.put("firstName", req.firstName());
        me.put("lastName", req.lastName());
        me.put("updatedAt", Instant.now().toString());
        me.put("version", UUID.randomUUID().toString());
        return ResponseEntity.ok(me);
    }
}
