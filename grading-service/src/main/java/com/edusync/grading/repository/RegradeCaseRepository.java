package com.edusync.grading.repository;

import com.edusync.grading.domain.RegradeCase;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * WHY a repository interface exists at all: see GradeRecordRepository's
 * Javadoc — same ARC-01/ARC-03/AR-03 rationale applies here.
 */
public interface RegradeCaseRepository extends JpaRepository<RegradeCase, String> {
}
