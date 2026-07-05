package com.edusync.auth.api;

import com.edusync.auth.api.dto.*;
import com.edusync.auth.domain.User;
import com.edusync.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * WHY this controller is now thin: it previously held the user/refresh-token
 * ConcurrentHashMap fields directly and performed password verification and
 * JWT issuance inline (MAIN-04 in the code review — five responsibilities in
 * one class). All of that now lives in AuthService; this class only
 * translates HTTP requests to service calls and maps results back to
 * ResponseEntity, exactly mirroring the CourseController/EnrollmentController
 * pattern used elsewhere in this remediation pass.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        User created = authService.register(request.email(), request.password(), request.firstName(), request.lastName());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDto.from(created));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponseDto> login(
            @Valid @RequestBody LoginRequestDto request,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        AuthService.TokenPair tokens = authService.login(request.email(), request.password(), tenantId);
        return ResponseEntity.ok(toResponse(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponseDto> refresh(@Valid @RequestBody RefreshRequestDto request) {
        AuthService.TokenPair tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(toResponse(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequestDto request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private TokenPairResponseDto toResponse(AuthService.TokenPair tokens) {
        return new TokenPairResponseDto(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresInSeconds());
    }
}
