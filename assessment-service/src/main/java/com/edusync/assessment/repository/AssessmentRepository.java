package com.edusync.assessment.repository;

import com.edusync.assessment.domain.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * WHY a repository interface exists at all: mirrors CourseRepository/
 * EnrollmentRepository — AssessmentService depends on this interface (not a
 * concrete Map), which is what makes it unit-testable in isolation with a
 * Mockito mock (see AssessmentServiceTest).
 */
public interface AssessmentRepository extends JpaRepository<Assessment, String> {
}
