package com.edusync.grading.repository;

import com.edusync.grading.domain.GradeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * WHY a repository interface exists at all: ARC-01/ARC-03/AR-03 in both
 * audits flagged that no repository abstraction existed anywhere in the
 * codebase — controllers held ConcurrentHashMap fields directly. Spring Data
 * generates the implementation; GradingService depends on this interface
 * (not a concrete Map), which is what makes GradingService unit-testable in
 * isolation with a Mockito mock (see GradingServiceTest).
 */
public interface GradeRecordRepository extends JpaRepository<GradeRecord, String> {

    Optional<GradeRecord> findBySubmissionId(String submissionId);
}
