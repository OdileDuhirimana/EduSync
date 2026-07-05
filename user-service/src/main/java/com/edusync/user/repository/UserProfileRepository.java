package com.edusync.user.repository;

import com.edusync.user.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * WHY a repository interface exists at all: mirrors CourseRepository/
 * EnrollmentRepository — Spring Data generates the implementation, and
 * UserProfileService depends on this interface (not a concrete Map), which
 * is what makes it unit-testable in isolation with a Mockito mock (see
 * UserProfileServiceTest).
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
}
