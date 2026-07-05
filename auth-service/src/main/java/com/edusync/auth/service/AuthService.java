package com.edusync.auth.service;

import com.edusync.auth.domain.RefreshToken;
import com.edusync.auth.domain.User;
import com.edusync.auth.repository.RefreshTokenRepository;
import com.edusync.auth.repository.UserRepository;
import com.edusync.auth.security.JwtService;
import com.edusync.common.error.ConflictException;
import com.edusync.common.error.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for account registration, credential verification, and
 * token-pair issuance/rotation — extracted out of AuthController.
 *
 * WHY this class exists (fixes ARC-01/MAIN-04 — "AuthController owns HTTP
 * routing, password hashing orchestration, JWT issuance orchestration,
 * in-memory user persistence, and in-memory refresh-token persistence — five
 * distinct responsibilities in one class"): AuthController now only parses
 * the HTTP request into a DTO and maps this service's return value into a
 * ResponseEntity. Every rule below (duplicate-email rejection, credential
 * verification, refresh-token rotation) is unit-tested in AuthServiceTest
 * with UserRepository/RefreshTokenRepository/JwtService/PasswordEncoder all
 * mocked — none of that was previously possible without spinning up
 * MockMvc + the full Spring context.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    public User register(String email, String rawPassword, String firstName, String lastName) {
        String normalizedEmail = normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User(
                UUID.randomUUID().toString(),
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                firstName,
                lastName,
                List.of("STUDENT"));
        return userRepository.save(user);
    }

    public TokenPair login(String email, String rawPassword, String tenantId) {
        String normalizedEmail = normalize(email);
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !passwordEncoder.matches(rawPassword, user.passwordHash())) {
            // WHY the same error for "no such user" and "wrong password":
            // returning a distinct "user not found" response would let an
            // attacker enumerate registered email addresses — a standard
            // "fail securely" requirement for authentication endpoints.
            throw new UnauthorizedException("Invalid email or password");
        }
        return issueTokenPair(user, resolveTenant(tenantId));
    }

    public TokenPair refresh(String refreshTokenValue) {
        // WHY consume (atomic remove) rather than a plain lookup: this
        // enforces single-use refresh tokens (rotation), matching the
        // original behavior and preventing a leaked refresh token from
        // being replayed after it has already been exchanged once.
        RefreshToken previous = refreshTokenRepository.consume(refreshTokenValue)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or already used"));

        User user = userRepository.findById(previous.userId())
                .orElseThrow(() -> new UnauthorizedException("User associated with this token no longer exists"));

        return issueTokenPair(user, previous.tenantId());
    }

    public void logout(String refreshTokenValue) {
        refreshTokenRepository.deleteByToken(refreshTokenValue);
    }

    private TokenPair issueTokenPair(User user, String tenantId) {
        String access = jwtService.createAccessToken(user.id(), user.email(), user.roles(), tenantId);
        String refreshValue = "r-" + UUID.randomUUID();
        refreshTokenRepository.save(new RefreshToken(refreshValue, user.id(), tenantId, Instant.now(clock)));
        return new TokenPair(access, refreshValue, jwtService.getAccessTtlSeconds());
    }

    private String resolveTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId;
    }

    private String normalize(String email) {
        return email.toLowerCase(Locale.ROOT).trim();
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
    }
}
