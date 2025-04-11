package com.edusync.auth.api;

import com.edusync.auth.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Simple in-memory user store: email -> user
    private final Map<String, User> users = new ConcurrentHashMap<>();
    // In-memory refresh token store: refreshToken -> {userId, tenantId, issuedAt}
    private final Map<String, Refresh> refreshStore = new ConcurrentHashMap<>();

    public AuthController(PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    private record Refresh(String userId, String tenantId, Instant issuedAt) {}

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    public record RegisterRequest(@Email @NotBlank String email,
                                  @NotBlank String password,
                                  @NotBlank String firstName,
                                  @NotBlank String lastName) {}

    public record UserResponse(String id, String email, String firstName, String lastName) {}

    private record User(String id, String email, String passwordHash, String firstName, String lastName, List<String> roles) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        String emailKey = req.email().toLowerCase(Locale.ROOT).trim();
        if (users.containsKey(emailKey)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "EMAIL_EXISTS", "message", "Email already registered"));
        }
        String id = UUID.randomUUID().toString();
        String hash = passwordEncoder.encode(req.password());
        User user = new User(id, emailKey, hash, req.firstName(), req.lastName(), List.of("STUDENT"));
        users.put(emailKey, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(id, emailKey, req.firstName(), req.lastName()));
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password){}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        String emailKey = req.email().toLowerCase(Locale.ROOT).trim();
        User user = users.get(emailKey);
        if (user == null || !passwordEncoder.matches(req.password(), user.passwordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_CREDENTIALS"));
        }
        String actualTenant = tenantId == null ? "default" : tenantId;
        String access = jwtService.createAccessToken(user.id(), user.email(), user.roles(), actualTenant);
        String refresh = "r-" + UUID.randomUUID();
        refreshStore.put(refresh, new Refresh(user.id(), actualTenant, Instant.now()));
        Map<String, Object> body = new HashMap<>();
        body.put("accessToken", access);
        body.put("refreshToken", refresh);
        body.put("tokenType", "Bearer");
        body.put("expiresIn", 900);
        return ResponseEntity.ok(body);
    }

    public record RefreshRequest(@NotBlank String refreshToken) {}

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req) {
        Refresh r = refreshStore.remove(req.refreshToken()); // rotation: remove old
        if (r == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INVALID_REFRESH"));
        }
        // issue new pair
        // find user by id to reuse roles and email
        Optional<User> userOpt = users.values().stream().filter(u -> Objects.equals(u.id(), r.userId())).findFirst();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "USER_NOT_FOUND"));
        }
        User user = userOpt.get();
        String access = jwtService.createAccessToken(user.id(), user.email(), user.roles(), r.tenantId());
        String newRefresh = "r-" + UUID.randomUUID();
        refreshStore.put(newRefresh, new Refresh(user.id(), r.tenantId(), Instant.now()));
        Map<String, Object> body = new HashMap<>();
        body.put("accessToken", access);
        body.put("refreshToken", newRefresh);
        body.put("tokenType", "Bearer");
        body.put("expiresIn", 900);
        return ResponseEntity.ok(body);
    }

    public record LogoutRequest(@NotBlank String refreshToken) {}

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody LogoutRequest req) {
        refreshStore.remove(req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
