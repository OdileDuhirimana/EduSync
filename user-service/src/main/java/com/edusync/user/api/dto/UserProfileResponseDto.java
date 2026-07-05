package com.edusync.user.api.dto;

import com.edusync.user.domain.UserProfile;

import java.time.Instant;

/**
 * WHY there is no "version": UUID.randomUUID().toString() field here (unlike
 * the previous response shape): that field was never real optimistic-locking
 * data, just a random value minted per response. `version` below is the
 * actual JPA @Version number persisted on UserProfile.
 */
public record UserProfileResponseDto(
        String id,
        String email,
        String firstName,
        String lastName,
        Instant updatedAt,
        long version
) {
    public static UserProfileResponseDto from(UserProfile profile) {
        return new UserProfileResponseDto(
                profile.getId(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getUpdatedAt(),
                profile.getVersion());
    }
}
