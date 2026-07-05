package com.edusync.grading.service;

import com.edusync.common.error.BadRequestException;
import com.edusync.common.error.ConflictException;
import com.edusync.common.error.NotFoundException;
import com.edusync.grading.api.dto.AnswerDto;
import com.edusync.grading.api.dto.AnswerKeyEntryDto;
import com.edusync.grading.domain.GradeRecord;
import com.edusync.grading.domain.GradeStatus;
import com.edusync.grading.domain.RegradeCase;
import com.edusync.grading.domain.RegradeStatus;
import com.edusync.grading.repository.GradeRecordRepository;
import com.edusync.grading.repository.RegradeCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic for grading and the regrade moderation workflow, extracted
 * out of GradingController.
 *
 * WHY this class exists (fixes ARC-01/ARC-03/AR-03, the single most-repeated
 * finding across both audits): the previous GradingController mixed HTTP
 * routing, persistence (two ConcurrentHashMap fields), and business rules
 * (score computation, the regrade state machine) in one class. This service
 * depends only on the GradeRecordRepository/RegradeCaseRepository
 * abstractions (constructor-injected, per SOLID's Dependency Inversion
 * Principle), which is what makes it testable via GradingServiceTest with
 * mocked repositories — no Spring context, no HTTP layer, no database
 * required to test the rules below.
 *
 * A java.time.Clock is injected (rather than calling Instant.now() directly)
 * so tests can assert exact timestamps deterministically instead of using
 * fragile "within N seconds" comparisons — the same pattern as
 * CourseService/EnrollmentService.
 */
@Service
public class GradingService {

    private static final String AUTO_GRADE_FEEDBACK = "Auto-graded result";

    private final GradeRecordRepository gradeRecordRepository;
    private final RegradeCaseRepository regradeCaseRepository;
    private final Clock clock;

    public GradingService(GradeRecordRepository gradeRecordRepository,
                           RegradeCaseRepository regradeCaseRepository,
                           Clock clock) {
        this.gradeRecordRepository = gradeRecordRepository;
        this.regradeCaseRepository = regradeCaseRepository;
        this.clock = clock;
    }

    /**
     * Real, deterministic, exact-match weighted-rubric auto-grader — replaces
     * the previous placeholder ({@code int total = 50 + Math.abs(submissionId
     * .hashCode() % 51);}), which produced a score with no relationship to the
     * submitted answers at all (a Critical Issue in the code review, item 13
     * of the remediation plan).
     * <p>
     * For each answer-key entry, the submitted answer with the matching
     * {@code questionId} (if any) is compared to the correct answer
     * case-insensitively and trimmed; a match awards that entry's full point
     * value, anything else (including no submitted answer at all) awards
     * zero. The final score is
     * {@code round(sum(awarded points) / sum(possible points) * 100)}.
     * <p>
     * WHY the caller supplies the answer key inline instead of this service
     * fetching it from assessment-service: grading-service has no
     * compile-time or HTTP dependency on assessment-service/submission-service
     * in this pass. Wiring a real client (REST or otherwise) to fetch the
     * canonical answer key and submitted answers server-side is a documented
     * follow-up integration opportunity — not a TODO silently left in this
     * method, since the method is complete and correct for the contract it
     * currently promises (scoring whatever answers/answer key it is given).
     *
     * @throws BadRequestException if the answer key's total possible points is
     *                              zero, which would otherwise require dividing
     *                              by zero or fabricating a score. Bean
     *                              Validation's {@code @Positive} on each
     *                              {@link AnswerKeyEntryDto#points()} already
     *                              guards this at the HTTP boundary; this check
     *                              is defense in depth for any caller that
     *                              bypasses DTO validation (e.g. a future
     *                              internal caller), consistent with DB-01's
     *                              "never rely solely on one layer" principle
     *                              already applied to enrollment-service.
     */
    @Transactional
    public GradeRecord autoGrade(String submissionId, List<AnswerDto> answers, List<AnswerKeyEntryDto> answerKey) {
        Map<String, String> submittedByQuestion = new LinkedHashMap<>();
        for (AnswerDto answer : answers) {
            submittedByQuestion.put(answer.questionId(), answer.response());
        }

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        int totalPossible = 0;
        int totalAwarded = 0;
        for (AnswerKeyEntryDto entry : answerKey) {
            totalPossible += entry.points();
            String submitted = submittedByQuestion.get(entry.questionId());
            boolean isMatch = submitted != null
                    && submitted.trim().equalsIgnoreCase(entry.correctAnswer().trim());
            int awarded = isMatch ? entry.points() : 0;
            totalAwarded += awarded;
            breakdown.put(entry.questionId(), awarded);
        }

        if (totalPossible == 0) {
            throw new BadRequestException(
                    "ANSWER_KEY_HAS_NO_POINTS: the supplied answer key has no positive point value to score against");
        }

        int total = Math.round((totalAwarded * 100f) / totalPossible);
        Instant now = Instant.now(clock);
        GradeRecord grade = findOrCreateGradeRecord(submissionId, now);
        grade.applyGrading(breakdown, total, AUTO_GRADE_FEEDBACK, now);
        return gradeRecordRepository.save(grade);
    }

    /**
     * Manual grading: the instructor supplies the per-question breakdown
     * directly, and the total is the sum of its values (matching the previous
     * controller's manual-grade behavior).
     */
    @Transactional
    public GradeRecord manualGrade(String submissionId, Map<String, Integer> breakdown, String feedback) {
        int total = breakdown.values().stream().mapToInt(Integer::intValue).sum();
        Instant now = Instant.now(clock);
        GradeRecord grade = findOrCreateGradeRecord(submissionId, now);
        grade.applyGrading(breakdown, total, feedback, now);
        return gradeRecordRepository.save(grade);
    }

    /**
     * WHY this is a read-only lookup rather than a persisted status
     * transition: the current {@link GradeRecord} schema (created in this
     * remediation pass) models GRADED/GRADED_OVERRIDDEN only — there is no
     * separate "published" visibility flag to flip, matching the previous
     * controller's behavior of not persisting anything for /publish either
     * (it only echoed back the current score plus a "GRADE_PUBLISHED" event
     * marker). Adding a persisted "published to student" flag/timestamp is a
     * reasonable next increment but is out of scope for this pass; this
     * method's actual, documented contract is "assert the submission has a
     * grade and return it," which is what it does.
     */
    @Transactional(readOnly = true)
    public GradeRecord publish(String submissionId) {
        return getGradeBySubmissionId(submissionId);
    }

    /**
     * WHY any authenticated caller (including the submission's own student)
     * may call this, unlike manual/auto grading and regrade decisions: a
     * student requesting review of their own grade is the normal, expected
     * use of this endpoint — restricting it to INSTRUCTOR/ADMIN would block
     * the very users this workflow exists for. Authorization here is
     * "must be an authenticated identity" (enforced by GradingController's
     * required {@code X-User-Id} header), not a role check.
     */
    @Transactional
    public RegradeCase requestRegrade(String submissionId, String requestedBy, String reason) {
        // A regrade only makes sense against a submission that already has a
        // grade — fail fast with a clear NotFoundException rather than
        // creating an orphaned regrade case with nothing to regrade.
        getGradeBySubmissionId(submissionId);

        Instant now = Instant.now(clock);
        RegradeCase regradeCase = new RegradeCase(
                UUID.randomUUID().toString(), submissionId, requestedBy, reason, RegradeStatus.PENDING, now);
        return regradeCaseRepository.save(regradeCase);
    }

    /**
     * Regrade decision state machine: PENDING -> APPROVED or PENDING ->
     * REJECTED. Deciding an already-decided case is rejected as a conflict
     * (idempotent retries of a decision are not supported — a moderator who
     * wants to change their mind must do so through a new business process,
     * not by silently re-calling this endpoint) and an unrecognized decision
     * string is rejected as a bad request, matching the previous controller's
     * behavior but now as typed exceptions instead of hand-built
     * ResponseEntity bodies.
     */
    @Transactional
    public RegradeCase decideRegrade(String requestId, String moderatorId, String decisionRaw,
                                      String note, Integer overrideTotal) {
        RegradeCase regradeCase = getRegradeCase(requestId);
        if (regradeCase.getStatus() != RegradeStatus.PENDING) {
            throw new ConflictException("ALREADY_DECIDED: regrade case '" + requestId + "' has already been decided");
        }

        String normalizedDecision = decisionRaw == null ? "" : decisionRaw.trim().toUpperCase(Locale.ROOT);
        Instant now = Instant.now(clock);

        if ("APPROVE".equals(normalizedDecision)) {
            regradeCase.approve(moderatorId, note, overrideTotal, now);
            if (overrideTotal != null) {
                GradeRecord grade = getGradeBySubmissionId(regradeCase.getSubmissionId());
                grade.applyOverride(overrideTotal, now);
                gradeRecordRepository.save(grade);
            }
        } else if ("REJECT".equals(normalizedDecision)) {
            regradeCase.reject(moderatorId, note, now);
        } else {
            throw new BadRequestException("INVALID_DECISION: decision must be APPROVE or REJECT");
        }

        return regradeCaseRepository.save(regradeCase);
    }

    @Transactional(readOnly = true)
    public GradeRecord getGradeBySubmissionId(String submissionId) {
        return gradeRecordRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new NotFoundException("No grade exists for submission '" + submissionId + "'"));
    }

    @Transactional(readOnly = true)
    public RegradeCase getRegradeCase(String requestId) {
        return regradeCaseRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Regrade case '" + requestId + "' was not found"));
    }

    /**
     * WHY re-use an existing row keyed by submissionId instead of always
     * inserting: a submission has exactly one "current" grade (see
     * GradeRecord's class Javadoc for why this isn't an append-only history
     * table), so auto-grading or manually grading a submission a second time
     * updates that same row rather than violating the unique constraint on
     * `submission_id` (see V1__create_grading_tables.sql) with a duplicate.
     */
    private GradeRecord findOrCreateGradeRecord(String submissionId, Instant now) {
        return gradeRecordRepository.findBySubmissionId(submissionId)
                .orElseGet(() -> new GradeRecord(
                        UUID.randomUUID().toString(), submissionId, Map.of(), 0, null, GradeStatus.GRADED, now, now));
    }
}
