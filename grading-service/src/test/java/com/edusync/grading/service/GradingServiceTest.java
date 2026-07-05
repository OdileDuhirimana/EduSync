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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * True unit tests for GradingService: both repository collaborators are
 * mocked with Mockito, so these tests exercise business rules (score
 * computation, the regrade state machine) in complete isolation from Spring,
 * HTTP, and the database — the same TEST-01-addressing pattern as
 * CourseServiceTest/EnrollmentServiceTest.
 */
class GradingServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2030-01-01T00:00:00Z");

    private GradeRecordRepository gradeRecordRepository;
    private RegradeCaseRepository regradeCaseRepository;
    private GradingService gradingService;

    @BeforeEach
    void setUp() {
        gradeRecordRepository = mock(GradeRecordRepository.class);
        regradeCaseRepository = mock(RegradeCaseRepository.class);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        gradingService = new GradingService(gradeRecordRepository, regradeCaseRepository, fixedClock);
    }

    @Test
    void autoGradeComputesGenuineWeightedScoreFromAnswerKey() {
        when(gradeRecordRepository.findBySubmissionId("sub-1")).thenReturn(Optional.empty());
        when(gradeRecordRepository.save(any(GradeRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        List<AnswerDto> answers = List.of(
                new AnswerDto("q1", " A "),
                new AnswerDto("q2", "wrong"));
        List<AnswerKeyEntryDto> answerKey = List.of(
                new AnswerKeyEntryDto("q1", "a", 60),
                new AnswerKeyEntryDto("q2", "b", 40));

        GradeRecord result = gradingService.autoGrade("sub-1", answers, answerKey);

        // q1 matches case-insensitively/trimmed ("A" vs "a") -> full 60 points;
        // q2 does not match -> 0 of 40. total = round((60 / 100) * 100) = 60.
        assertThat(result.getTotal()).isEqualTo(60);
        assertThat(result.getBreakdown()).containsEntry("q1", 60).containsEntry("q2", 0);
        assertThat(result.getStatus()).isEqualTo(GradeStatus.GRADED);
        assertThat(result.getSubmissionId()).isEqualTo("sub-1");
        assertThat(result.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(result.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void autoGradeScoresZeroForNonMatchingOrMissingAnswer() {
        when(gradeRecordRepository.findBySubmissionId("sub-2")).thenReturn(Optional.empty());
        when(gradeRecordRepository.save(any(GradeRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        // No answer submitted at all for q1.
        List<AnswerDto> answers = List.of();
        List<AnswerKeyEntryDto> answerKey = List.of(new AnswerKeyEntryDto("q1", "correct", 100));

        GradeRecord result = gradingService.autoGrade("sub-2", answers, answerKey);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getBreakdown()).containsEntry("q1", 0);
    }

    @Test
    void autoGradeThrowsBadRequestWhenAnswerKeyHasNoPoints() {
        // Bypasses DTO-level @Positive validation to exercise the service's
        // own defense-in-depth check (see GradingService#autoGrade Javadoc).
        List<AnswerDto> answers = List.of(new AnswerDto("q1", "a"));
        List<AnswerKeyEntryDto> answerKey = List.of(new AnswerKeyEntryDto("q1", "a", 0));

        assertThatThrownBy(() -> gradingService.autoGrade("sub-3", answers, answerKey))
                .isInstanceOf(BadRequestException.class);

        verify(gradeRecordRepository, never()).save(any());
    }

    @Test
    void manualGradeComputesTotalFromBreakdownSum() {
        when(gradeRecordRepository.findBySubmissionId("sub-4")).thenReturn(Optional.empty());
        when(gradeRecordRepository.save(any(GradeRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        GradeRecord result = gradingService.manualGrade("sub-4", Map.of("q1", 30, "q2", 40), "Nice work");

        assertThat(result.getTotal()).isEqualTo(70);
        assertThat(result.getFeedback()).isEqualTo("Nice work");
        assertThat(result.getStatus()).isEqualTo(GradeStatus.GRADED);
    }

    @Test
    void publishReturnsExistingGrade() {
        GradeRecord existing = new GradeRecord(
                "grade-1", "sub-5", Map.of("q1", 50), 50, "ok", GradeStatus.GRADED, FIXED_NOW, FIXED_NOW);
        when(gradeRecordRepository.findBySubmissionId("sub-5")).thenReturn(Optional.of(existing));

        GradeRecord result = gradingService.publish("sub-5");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void publishThrowsNotFoundWhenNoGradeExists() {
        when(gradeRecordRepository.findBySubmissionId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gradingService.publish("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requestRegradeThrowsNotFoundWhenSubmissionHasNoGrade() {
        when(gradeRecordRepository.findBySubmissionId("sub-6")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gradingService.requestRegrade("sub-6", "student-1", "please review"))
                .isInstanceOf(NotFoundException.class);

        verify(regradeCaseRepository, never()).save(any());
    }

    @Test
    void requestRegradeCreatesPendingCaseWhenGradeExists() {
        GradeRecord existing = new GradeRecord(
                "grade-2", "sub-7", Map.of("q1", 50), 50, "ok", GradeStatus.GRADED, FIXED_NOW, FIXED_NOW);
        when(gradeRecordRepository.findBySubmissionId("sub-7")).thenReturn(Optional.of(existing));
        when(regradeCaseRepository.save(any(RegradeCase.class))).thenAnswer(inv -> inv.getArgument(0));

        RegradeCase result = gradingService.requestRegrade("sub-7", "student-1", "please review");

        assertThat(result.getStatus()).isEqualTo(RegradeStatus.PENDING);
        assertThat(result.getRequestedBy()).isEqualTo("student-1");
        assertThat(result.getReason()).isEqualTo("please review");
        assertThat(result.getRequestedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void decideRegradeApprovedWithOverrideUpdatesBothCaseAndGrade() {
        GradeRecord existingGrade = new GradeRecord(
                "grade-3", "sub-8", Map.of("q1", 50), 50, "ok", GradeStatus.GRADED,
                FIXED_NOW.minusSeconds(60), FIXED_NOW.minusSeconds(60));
        RegradeCase pending = new RegradeCase(
                "req-1", "sub-8", "student-1", "reason", RegradeStatus.PENDING, FIXED_NOW.minusSeconds(30));
        when(regradeCaseRepository.findById("req-1")).thenReturn(Optional.of(pending));
        when(gradeRecordRepository.findBySubmissionId("sub-8")).thenReturn(Optional.of(existingGrade));
        when(gradeRecordRepository.save(any(GradeRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(regradeCaseRepository.save(any(RegradeCase.class))).thenAnswer(inv -> inv.getArgument(0));

        RegradeCase decided = gradingService.decideRegrade("req-1", "instructor-1", "approve", "Accepted", 85);

        assertThat(decided.getStatus()).isEqualTo(RegradeStatus.APPROVED);
        assertThat(decided.getDecidedBy()).isEqualTo("instructor-1");
        assertThat(decided.getDecisionNote()).isEqualTo("Accepted");
        assertThat(decided.getOverrideTotal()).isEqualTo(85);
        assertThat(decided.getDecidedAt()).isEqualTo(FIXED_NOW);

        assertThat(existingGrade.getTotal()).isEqualTo(85);
        assertThat(existingGrade.getStatus()).isEqualTo(GradeStatus.GRADED_OVERRIDDEN);
        assertThat(existingGrade.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void decideRegradeApprovedWithoutOverrideLeavesGradeUnchanged() {
        GradeRecord existingGrade = new GradeRecord(
                "grade-4", "sub-9", Map.of("q1", 50), 50, "ok", GradeStatus.GRADED, FIXED_NOW, FIXED_NOW);
        RegradeCase pending = new RegradeCase(
                "req-2", "sub-9", "student-1", "reason", RegradeStatus.PENDING, FIXED_NOW);
        when(regradeCaseRepository.findById("req-2")).thenReturn(Optional.of(pending));
        when(regradeCaseRepository.save(any(RegradeCase.class))).thenAnswer(inv -> inv.getArgument(0));

        RegradeCase decided = gradingService.decideRegrade("req-2", "instructor-1", "APPROVE", "fine as-is", null);

        assertThat(decided.getStatus()).isEqualTo(RegradeStatus.APPROVED);
        assertThat(decided.getOverrideTotal()).isNull();
        assertThat(existingGrade.getTotal()).isEqualTo(50);
        assertThat(existingGrade.getStatus()).isEqualTo(GradeStatus.GRADED);
        verify(gradeRecordRepository, never()).save(any());
    }

    @Test
    void decideRegradeRejectedDoesNotChangeGrade() {
        GradeRecord existingGrade = new GradeRecord(
                "grade-5", "sub-10", Map.of("q1", 50), 50, null, GradeStatus.GRADED, FIXED_NOW, FIXED_NOW);
        RegradeCase pending = new RegradeCase(
                "req-3", "sub-10", "student-1", "reason", RegradeStatus.PENDING, FIXED_NOW);
        when(regradeCaseRepository.findById("req-3")).thenReturn(Optional.of(pending));
        when(regradeCaseRepository.save(any(RegradeCase.class))).thenAnswer(inv -> inv.getArgument(0));

        RegradeCase decided = gradingService.decideRegrade("req-3", "instructor-1", "REJECT", "Not valid", null);

        assertThat(decided.getStatus()).isEqualTo(RegradeStatus.REJECTED);
        assertThat(existingGrade.getTotal()).isEqualTo(50);
        assertThat(existingGrade.getStatus()).isEqualTo(GradeStatus.GRADED);
        verify(gradeRecordRepository, never()).save(any());
    }

    @Test
    void decideRegradeOnAlreadyDecidedCaseThrowsConflict() {
        RegradeCase decidedAlready = new RegradeCase(
                "req-4", "sub-11", "student-1", "reason", RegradeStatus.APPROVED, FIXED_NOW);
        when(regradeCaseRepository.findById("req-4")).thenReturn(Optional.of(decidedAlready));

        assertThatThrownBy(() -> gradingService.decideRegrade("req-4", "instructor-1", "APPROVE", null, null))
                .isInstanceOf(ConflictException.class);

        verify(regradeCaseRepository, never()).save(any());
    }

    @Test
    void decideRegradeWithInvalidDecisionStringThrowsBadRequest() {
        RegradeCase pending = new RegradeCase(
                "req-5", "sub-12", "student-1", "reason", RegradeStatus.PENDING, FIXED_NOW);
        when(regradeCaseRepository.findById("req-5")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> gradingService.decideRegrade("req-5", "instructor-1", "MAYBE", null, null))
                .isInstanceOf(BadRequestException.class);

        verify(regradeCaseRepository, never()).save(any());
        verify(gradeRecordRepository, never()).save(any());
    }

    @Test
    void decideRegradeThrowsNotFoundForUnknownRequestId() {
        when(regradeCaseRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gradingService.decideRegrade("missing", "instructor-1", "APPROVE", null, null))
                .isInstanceOf(NotFoundException.class);
    }
}
