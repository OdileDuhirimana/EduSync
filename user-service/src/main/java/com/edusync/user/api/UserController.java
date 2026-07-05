package com.edusync.user.api;

import com.edusync.user.api.dto.UpdateProfileRequestDto;
import com.edusync.user.api.dto.UserProfileResponseDto;
import com.edusync.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * WHY this controller is now "thin" (no persistence, no business rules): it
 * only translates HTTP <-> domain — parsing the trusted X-User-Id/
 * X-User-Email headers, delegating to UserProfileService, and mapping the
 * returned UserProfile entity to a response DTO. All persistence decisions
 * live in UserProfileService and the JPA repository respectively.
 *
 * WHY neither endpoint has a `caller.requireRole(...)` check: both are
 * strictly "read/write MY OWN profile" endpoints, scoped entirely by the
 * caller's own X-User-Id — a value the gateway derives from that caller's
 * own verified JWT and that this service cannot be tricked into accepting
 * for a different identity. There is no operation here that acts on anyone
 * else's data, so there is no role distinction to enforce (unlike
 * CourseController's create/publish, which act on shared resources and
 * therefore do require INSTRUCTOR/ADMIN). This is a deliberate scope
 * decision, not an oversight.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    // WHY `required` is no longer set to false: a missing X-User-Id means the
    // gateway did not authenticate this caller at all. Letting Spring's
    // MissingRequestHeaderException flow to GlobalExceptionHandler yields a
    // clean, uniform 400 ApiError ("MISSING_HEADER") instead of this
    // controller hand-rolling a 401 Map response, as it did before.
    @GetMapping("/me")
    public UserProfileResponseDto me(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Email", required = false) String email) {
        return UserProfileResponseDto.from(userProfileService.getOrCreate(userId, email));
    }

    @PatchMapping("/me")
    public UserProfileResponseDto updateMe(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequestDto request) {
        return UserProfileResponseDto.from(
                userProfileService.update(userId, request.firstName(), request.lastName()));
    }
}
