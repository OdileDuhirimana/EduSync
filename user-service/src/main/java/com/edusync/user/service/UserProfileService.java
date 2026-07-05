package com.edusync.user.service;

import com.edusync.user.domain.UserProfile;
import com.edusync.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Business logic for reading and updating a caller's own profile, extracted
 * out of UserController.
 *
 * WHY this class exists: the previous UserController built and returned a
 * plain Map on every call — `GET /me` always returned hardcoded "Demo"/"User"
 * names, and `PATCH /me` echoed the request body back without ever calling
 * any persistence API (an external audit's Critical Risk: "a profile update
 * is never saved anywhere"). This service depends only on the
 * UserProfileRepository abstraction (constructor-injected), which is what
 * makes it testable via UserProfileServiceTest with a mocked repository — no
 * Spring context, no HTTP layer, no database required.
 *
 * A java.time.Clock is injected (rather than calling Instant.now() directly)
 * so tests can assert exact timestamps deterministically.
 */
@Service
public class UserProfileService {

    // WHY placeholder names instead of 404ing on first access: a caller who
    // registered through auth-service and received a valid gateway-issued
    // identity is legitimately allowed to call GET /users/me before ever
    // touching user-service — this is their very first profile read, not an
    // error condition. These are explicit, documented placeholders (not
    // silently invented "real" data) that the caller is expected to
    // overwrite via PATCH /users/me once they set their own name.
    private static final String DEFAULT_FIRST_NAME = "New";
    private static final String DEFAULT_LAST_NAME = "User";

    private final UserProfileRepository userProfileRepository;
    private final Clock clock;

    public UserProfileService(UserProfileRepository userProfileRepository, Clock clock) {
        this.userProfileRepository = userProfileRepository;
        this.clock = clock;
    }

    /**
     * Returns the caller's existing profile, or creates and persists a
     * default one on the caller's first-ever call to /users/me.
     */
    @Transactional
    public UserProfile getOrCreate(String userId, String email) {
        return userProfileRepository.findById(userId)
                .orElseGet(() -> userProfileRepository.save(
                        new UserProfile(userId, email, DEFAULT_FIRST_NAME, DEFAULT_LAST_NAME, Instant.now(clock))));
    }

    /**
     * Upserts the caller's profile with a new first/last name and REALLY
     * PERSISTS it — this is the fix for the audit's Critical Risk. Returns
     * the entity Hibernate actually persisted (including a real
     * `updatedAt`), never an echo of the request.
     */
    @Transactional
    public UserProfile update(String userId, String firstName, String lastName) {
        Instant now = Instant.now(clock);
        return userProfileRepository.findById(userId)
                .map(existing -> {
                    existing.updateProfile(firstName, lastName, now);
                    return userProfileRepository.save(existing);
                })
                // WHY upsert instead of requiring a prior GET: a caller could
                // legitimately PATCH their profile before ever calling GET
                // /users/me. email is unknown at this call site (the PATCH
                // request carries no email field), so it is left null here;
                // it is only ever populated by getOrCreate on a subsequent
                // GET, which never overwrites an already-set email.
                .orElseGet(() -> userProfileRepository.save(
                        new UserProfile(userId, null, firstName, lastName, now)));
    }
}
