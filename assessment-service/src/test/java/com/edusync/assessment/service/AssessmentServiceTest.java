package com.edusync.assessment.service;

import com.edusync.assessment.domain.Assessment;
import com.edusync.assessment.domain.AssessmentSession;
import com.edusync.assessment.domain.AssessmentType;
import com.edusync.assessment.repository.AssessmentRepository;
import com.edusync.assessment.repository.AssessmentSessionRepository;
import com.edusync.common.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * True unit tests for AssessmentService: both repository collaborators are
 * mocked with Mockito, so these tests exercise business rules in complete
 * isolation from Spring, HTTP, and the database — the same pattern as
 * CourseServiceTest/EnrollmentServiceTest.
 */
class AssessmentServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2030-01-01T00:00:00Z");

    private AssessmentRepository assessmentRepository;
    private AssessmentSessionRepository sessionRepository;
    private AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        assessmentRepository = mock(AssessmentRepository.class);
        sessionRepository = mock(AssessmentSessionRepository.class);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        assessmentService = new AssessmentService(assessmentRepository, sessionRepository, fixedClock);
    }

    @Test
    void createPersistsNewAssessmentWithGeneratedIdAndTimestamps() {
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(inv -> inv.getArgument(0));

        Assessment created = assessmentService.create("course-1", "Midterm", AssessmentType.EXAM);

        ArgumentCaptor<Assessment> captor = ArgumentCaptor.forClass(Assessment.class);
        verify(assessmentRepository).save(captor.capture());
        Assessment saved = captor.getValue();

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getCourseId()).isEqualTo("course-1");
        assertThat(saved.getTitle()).isEqualTo("Midterm");
        assertThat(saved.getType()).isEqualTo(AssessmentType.EXAM);
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        assertThat(created).isSameAs(saved);
    }

    @Test
    void createDefaultsToQuizWhenTypeIsNull() {
        when(assessmentRepository.save(any(Assessment.class))).thenAnswer(inv -> inv.getArgument(0));

        Assessment created = assessmentService.create("course-1", "Pop Quiz", null);

        assertThat(created.getType()).isEqualTo(AssessmentType.QUIZ);
    }

    @Test
    void getByIdReturnsAssessmentWhenFound() {
        Assessment existing = new Assessment("id-1", "course-1", "Midterm", AssessmentType.EXAM, FIXED_NOW, FIXED_NOW);
        when(assessmentRepository.findById("id-1")).thenReturn(Optional.of(existing));

        Assessment result = assessmentService.getById("id-1");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        when(assessmentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentService.getById("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void startCreatesNewSessionOnFirstCall() {
        Assessment existing = new Assessment("id-1", "course-1", "Midterm", AssessmentType.EXAM, FIXED_NOW, FIXED_NOW);
        when(assessmentRepository.findById("id-1")).thenReturn(Optional.of(existing));
        when(sessionRepository.findByAssessmentIdAndUserId("id-1", "user-1")).thenReturn(Optional.empty());
        when(sessionRepository.save(any(AssessmentSession.class))).thenAnswer(inv -> inv.getArgument(0));

        AssessmentSession session = assessmentService.start("id-1", "user-1");

        assertThat(session.getAssessmentId()).isEqualTo("id-1");
        assertThat(session.getUserId()).isEqualTo("user-1");
        assertThat(session.getToken()).isNotBlank();
        assertThat(session.getTimeLimitMinutes()).isEqualTo(AssessmentService.DEFAULT_TIME_LIMIT_MINUTES);
        assertThat(session.getStartedAt()).isEqualTo(FIXED_NOW);
        verify(sessionRepository).save(any(AssessmentSession.class));
    }

    /**
     * The idempotency rule this test protects: repeatedly calling start()
     * for the same (assessmentId, userId) pair must never mint a second
     * token, or a student could keep resetting their timed window.
     */
    @Test
    void startReturnsSameSessionOnSecondCallForSameAssessmentAndUser() {
        Assessment existing = new Assessment("id-1", "course-1", "Midterm", AssessmentType.EXAM, FIXED_NOW, FIXED_NOW);
        when(assessmentRepository.findById("id-1")).thenReturn(Optional.of(existing));
        when(sessionRepository.save(any(AssessmentSession.class))).thenAnswer(inv -> inv.getArgument(0));

        when(sessionRepository.findByAssessmentIdAndUserId("id-1", "user-1")).thenReturn(Optional.empty());
        AssessmentSession first = assessmentService.start("id-1", "user-1");

        ArgumentCaptor<AssessmentSession> captor = ArgumentCaptor.forClass(AssessmentSession.class);
        verify(sessionRepository).save(captor.capture());
        AssessmentSession persisted = captor.getValue();

        when(sessionRepository.findByAssessmentIdAndUserId("id-1", "user-1")).thenReturn(Optional.of(persisted));
        AssessmentSession second = assessmentService.start("id-1", "user-1");

        assertThat(second.getToken()).isEqualTo(first.getToken());
        assertThat(second.getId()).isEqualTo(first.getId());
        verify(sessionRepository, times(1)).save(any(AssessmentSession.class));
    }

    @Test
    void startThrowsNotFoundForUnknownAssessment() {
        when(assessmentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentService.start("missing", "user-1"))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(sessionRepository);
    }
}
