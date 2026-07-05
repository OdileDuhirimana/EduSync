package com.edusync.assessment.service;

import com.edusync.assessment.domain.Assessment;
import com.edusync.assessment.domain.AssessmentSession;
import com.edusync.assessment.domain.AssessmentType;
import com.edusync.assessment.repository.AssessmentRepository;
import com.edusync.assessment.repository.AssessmentSessionRepository;
import com.edusync.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for assessment authoring and timed-session lifecycle,
 * extracted out of AssessmentController.
 *
 * WHY this class exists (fixes the same ARC-01/ARC-03/AR-03-class finding
 * course-service and enrollment-service were already remediated for): the
 * previous AssessmentController mixed HTTP routing, persistence (a
 * ConcurrentHashMap field), and business rules in one class with zero
 * authorization. This service depends only on repository abstractions
 * (constructor-injected, per SOLID's Dependency Inversion Principle), which
 * is what makes it testable via AssessmentServiceTest with mocked
 * repositories — no Spring context, no HTTP layer, no database required to
 * test the rules below.
 *
 * A java.time.Clock is injected (rather than calling Instant.now()
 * directly) so tests can assert exact timestamps deterministically instead
 * of using fragile "within N seconds" comparisons.
 */
@Service
public class AssessmentService {

    /**
     * WHY a fixed default rather than a per-request/per-assessment setting:
     * the original controller hardcoded `timeLimitMin: 30` for every
     * session; no configurable-time-limit requirement exists yet for this
     * remediation pass, so the default is preserved as-is rather than
     * inventing an unrequested settings feature (YAGNI). A configurable
     * per-assessment time limit is a reasonable follow-up but is out of
     * scope here.
     */
    static final int DEFAULT_TIME_LIMIT_MINUTES = 30;

    private final AssessmentRepository assessmentRepository;
    private final AssessmentSessionRepository sessionRepository;
    private final Clock clock;

    public AssessmentService(AssessmentRepository assessmentRepository,
                              AssessmentSessionRepository sessionRepository,
                              Clock clock) {
        this.assessmentRepository = assessmentRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional
    public Assessment create(String courseId, String title, AssessmentType type) {
        Instant now = Instant.now(clock);
        AssessmentType resolvedType = type == null ? AssessmentType.QUIZ : type;
        Assessment assessment = new Assessment(
                UUID.randomUUID().toString(), courseId, title, resolvedType, now, now);
        return assessmentRepository.save(assessment);
    }

    @Transactional(readOnly = true)
    public Assessment getById(String id) {
        return assessmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Assessment '" + id + "' was not found"));
    }

    /**
     * WHY an existing session/token is returned instead of always minting a
     * new one: without this rule, a student could repeatedly call
     * `/start` to keep resetting startedAt and receive a fresh, longer time
     * window than intended — a real integrity concern for a timed
     * assessment (this was previously not enforced at all: the original
     * endpoint had no persistence, so every call trivially "succeeded" with
     * a brand new token). Checking for an existing (assessmentId, userId)
     * row first (application-level, in addition to the DB-level unique
     * index in V1__create_assessments_table.sql as defense in depth) makes
     * this idempotent per the sibling services' "never rely solely on
     * application-level checks" convention (see EnrollmentService#create).
     */
    @Transactional
    public AssessmentSession start(String assessmentId, String userId) {
        // Confirms the assessment exists before recording a session against
        // it; throws NotFoundException otherwise.
        getById(assessmentId);

        Optional<AssessmentSession> existing =
                sessionRepository.findByAssessmentIdAndUserId(assessmentId, userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        AssessmentSession session = new AssessmentSession(
                UUID.randomUUID().toString(),
                assessmentId,
                userId,
                "ast-" + UUID.randomUUID(),
                DEFAULT_TIME_LIMIT_MINUTES,
                Instant.now(clock));
        return sessionRepository.save(session);
    }
}
