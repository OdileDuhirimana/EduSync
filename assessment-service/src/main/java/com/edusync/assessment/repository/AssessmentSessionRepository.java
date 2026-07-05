package com.edusync.assessment.repository;

import com.edusync.assessment.domain.AssessmentSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, String> {

    /**
     * Backs the "has this user already started this assessment" check in
     * AssessmentService#start, which is what makes returning the existing
     * session/token (instead of minting a new one) possible.
     */
    Optional<AssessmentSession> findByAssessmentIdAndUserId(String assessmentId, String userId);
}
