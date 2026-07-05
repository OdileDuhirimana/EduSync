package com.edusync.user.service;

import com.edusync.user.domain.UserProfile;
import com.edusync.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * True unit tests for UserProfileService: the repository collaborator is
 * mocked with Mockito, so these tests exercise the actual bug fix (PATCH
 * really persisting, GET really returning a per-caller profile) in complete
 * isolation from Spring, HTTP, and the database.
 */
class UserProfileServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2030-01-01T00:00:00Z");

    private UserProfileRepository userProfileRepository;
    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileRepository = mock(UserProfileRepository.class);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        userProfileService = new UserProfileService(userProfileRepository, fixedClock);
    }

    @Test
    void getOrCreateCreatesNewProfileOnFirstCallAndReturnsSameProfileOnSecondCall() {
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfile firstCall = userProfileService.getOrCreate("user-1", "user1@example.com");

        assertThat(firstCall.getId()).isEqualTo("user-1");
        assertThat(firstCall.getEmail()).isEqualTo("user1@example.com");
        assertThat(firstCall.getFirstName()).isEqualTo("New");
        assertThat(firstCall.getLastName()).isEqualTo("User");
        assertThat(firstCall.getCreatedAt()).isEqualTo(FIXED_NOW);
        verify(userProfileRepository, times(1)).save(any(UserProfile.class));

        // Simulate the row now existing in the database on the second call.
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(firstCall));

        UserProfile secondCall = userProfileService.getOrCreate("user-1", "user1@example.com");

        assertThat(secondCall).isSameAs(firstCall);
        // save() must not be called again — the second call is a pure read.
        verify(userProfileRepository, times(1)).save(any(UserProfile.class));
    }

    @Test
    void updatePersistsNewNameForAnExistingProfile() {
        Instant createdAt = FIXED_NOW.minusSeconds(3600);
        UserProfile existing = new UserProfile("user-1", "user1@example.com", "Old", "Name", createdAt);
        when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfile updated = userProfileService.update("user-1", "Jane", "Doe");

        // This is the exact bug being fixed: assert save() actually happened
        // with the new values, not just that some response object came back.
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        UserProfile saved = captor.getValue();

        assertThat(saved.getFirstName()).isEqualTo("Jane");
        assertThat(saved.getLastName()).isEqualTo("Doe");
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getEmail()).isEqualTo("user1@example.com");
        assertThat(updated).isSameAs(saved);
    }

    @Test
    void updateCreatesNewProfileWhenNoneExistsYet() {
        when(userProfileRepository.findById("user-2")).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfile created = userProfileService.update("user-2", "Alice", "Wong");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        UserProfile saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo("user-2");
        assertThat(saved.getFirstName()).isEqualTo("Alice");
        assertThat(saved.getLastName()).isEqualTo("Wong");
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(created).isSameAs(saved);
    }
}
