package com.edusync.user.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity backing real persistence for a caller's own profile.
 *
 * WHY this replaces the previous behavior (UserController building and
 * returning a plain Map on every call, never saving anything): an external
 * audit found that `PATCH /users/me` echoed the request straight back to the
 * caller without persisting it anywhere, and `GET /users/me` returned
 * hardcoded "Demo"/"User" names for every caller. This entity is mapped by
 * Flyway migration V1__create_user_profiles_table.sql, which is the single
 * source of truth for the schema.
 *
 * WHY `id` is not `@GeneratedValue`: it must be exactly the userId string
 * the gateway asserts via the verified `X-User-Id` header, not a value this
 * service invents — otherwise a caller's profile row could never be found on
 * their next request. user-service does not call auth-service or share a
 * table with it; this id is simply the same opaque string, trusted because
 * the gateway is the only thing allowed to set that header.
 *
 * WHY `@Version` exists: the old API response included a
 * `"version": UUID.randomUUID().toString()` field that was never wired to
 * any actual optimistic-locking check — a new random value on every response,
 * not a real version. `@Version` gives this entity genuine JPA optimistic
 * locking (a concurrent PATCH from two requests for the same profile will
 * cause the losing write to fail with an OptimisticLockException rather than
 * silently overwriting the winner), and the number exposed to API clients is
 * the actual row version Hibernate maintains.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected UserProfile() {
        // required by JPA
    }

    public UserProfile(String id, String email, String firstName, String lastName, Instant now) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Applies a real, persisted name change — this is the actual bug fix: the old
     * controller built this exact response shape without ever calling save(). */
    public void updateProfile(String firstName, String lastName, Instant when) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.updatedAt = when;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
