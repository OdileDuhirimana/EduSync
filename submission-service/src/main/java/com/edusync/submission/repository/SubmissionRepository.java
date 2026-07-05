package com.edusync.submission.repository;

import com.edusync.submission.domain.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * WHY a repository interface exists at all: mirrors CourseRepository /
 * EnrollmentRepository — ARC-01/ARC-03/AR-03 in the audits flagged that no
 * repository abstraction existed anywhere in the codebase, controllers held
 * ConcurrentHashMap fields directly. Spring Data generates the
 * implementation; SubmissionService depends on this interface (not a
 * concrete Map), which is what makes SubmissionService unit-testable in
 * isolation with a Mockito mock (see SubmissionServiceTest).
 */
public interface SubmissionRepository extends JpaRepository<Submission, String> {

    /**
     * WHY this query exists (fixes the current code's
     * `store.values().stream().filter(...)` full-table-scan pattern): the
     * similarity endpoint only ever needs submissions for the same
     * assessment as the target, excluding the target itself. Pushing that
     * filter into the database (backed by ix_submissions_assessment, see the
     * Flyway migration) means the service never loads unrelated
     * assessments' submissions into memory, which matters once submission
     * volume grows beyond a handful of rows.
     */
    List<Submission> findByAssessmentIdAndIdNot(String assessmentId, String excludeId);
}
