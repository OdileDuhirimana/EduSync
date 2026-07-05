package com.edusync.auth.service;

import com.edusync.auth.domain.RefreshToken;
import com.edusync.auth.domain.User;
import com.edusync.auth.repository.RefreshTokenRepository;
import com.edusync.auth.repository.UserRepository;
import com.edusync.auth.security.JwtService;
import com.edusync.common.error.ConflictException;
import com.edusync.common.error.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService covering the flows the audits found completely
 * untested (TEST-05 in the code review: "AuthController's single test only
 * asserts /health returns 200... none of register/login/refresh/logout
 * logic... has any test coverage whatsoever, despite these being the
 * services with the highest security... sensitivity"). All four
 * collaborators are mocked, so no Spring context or database is needed.
 */
class AuthServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2030-01-01T00:00:00Z");

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, fixedClock);
    }

    @Test
    void registerCreatesUserWithHashedPasswordAndDefaultStudentRole() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("hashed-value");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = authService.register("USER@example.com", "plaintext-password", "Ada", "Lovelace");

        assertThat(created.email()).isEqualTo("user@example.com");
        assertThat(created.passwordHash()).isEqualTo("hashed-value");
        assertThat(created.roles()).containsExactly("STUDENT");
        verify(passwordEncoder).encode("plaintext-password");
    }

    @Test
    void registerRejectsDuplicateEmailRegardlessOfCase() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("User@Example.com", "password123", "Ada", "Lovelace"))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginRejectsUnknownEmailWithoutRevealingWhichPartFailed() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@example.com", "irrelevant", null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("u-1", "user@example.com", "hashed", "Ada", "Lovelace", List.of("STUDENT"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("user@example.com", "wrong-password", null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginIssuesTokenPairAndPersistsRefreshTokenOnSuccess() {
        User user = new User("u-1", "user@example.com", "hashed", "Ada", "Lovelace", List.of("STUDENT"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
        when(jwtService.createAccessToken(eq("u-1"), eq("user@example.com"), eq(List.of("STUDENT")), eq("default")))
                .thenReturn("signed-jwt");
        when(jwtService.getAccessTtlSeconds()).thenReturn(900L);

        AuthService.TokenPair tokens = authService.login("user@example.com", "correct-password", null);

        assertThat(tokens.accessToken()).isEqualTo("signed-jwt");
        assertThat(tokens.refreshToken()).startsWith("r-");
        assertThat(tokens.expiresInSeconds()).isEqualTo(900L);
        verify(refreshTokenRepository).save(argThat(rt ->
                rt.userId().equals("u-1") && rt.tenantId().equals("default") && rt.issuedAt().equals(FIXED_NOW)));
    }

    @Test
    void loginHonorsExplicitTenantId() {
        User user = new User("u-1", "user@example.com", "hashed", "Ada", "Lovelace", List.of("STUDENT"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "hashed")).thenReturn(true);
        when(jwtService.createAccessToken(any(), any(), any(), eq("acme-corp"))).thenReturn("signed-jwt");

        authService.login("user@example.com", "correct-password", "acme-corp");

        verify(jwtService).createAccessToken("u-1", "user@example.com", List.of("STUDENT"), "acme-corp");
    }

    @Test
    void refreshRotatesTokenAndRejectsReuseOfConsumedToken() {
        RefreshToken previous = new RefreshToken("r-old", "u-1", "default", FIXED_NOW.minusSeconds(60));
        when(refreshTokenRepository.consume("r-old")).thenReturn(Optional.of(previous));
        User user = new User("u-1", "user@example.com", "hashed", "Ada", "Lovelace", List.of("STUDENT"));
        when(userRepository.findById("u-1")).thenReturn(Optional.of(user));
        when(jwtService.createAccessToken(any(), any(), any(), any())).thenReturn("new-jwt");

        AuthService.TokenPair rotated = authService.refresh("r-old");

        assertThat(rotated.accessToken()).isEqualTo("new-jwt");

        // Second call: the repository mock now returns empty because a real
        // implementation would have already removed "r-old" on first consume.
        when(refreshTokenRepository.consume("r-old")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.refresh("r-old")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.consume("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("does-not-exist"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRejectsTokenWhoseUserNoLongerExists() {
        RefreshToken previous = new RefreshToken("r-old", "deleted-user", "default", FIXED_NOW);
        when(refreshTokenRepository.consume("r-old")).thenReturn(Optional.of(previous));
        when(userRepository.findById("deleted-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("r-old")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void logoutDeletesRefreshToken() {
        authService.logout("r-some-token");

        verify(refreshTokenRepository).deleteByToken("r-some-token");
    }
}
