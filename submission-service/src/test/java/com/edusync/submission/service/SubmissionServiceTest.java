package com.edusync.submission.service;

import com.edusync.common.error.BadRequestException;
import com.edusync.common.error.NotFoundException;
import com.edusync.submission.domain.Submission;
import com.edusync.submission.domain.SubmissionStatus;
import com.edusync.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
 * True unit tests for SubmissionService: the repository collaborator is
 * mocked with Mockito, so these tests exercise business rules — including
 * the relocated Jaccard-similarity algorithm — in complete isolation from
 * Spring, HTTP, and the database, mirroring CourseServiceTest/
 * EnrollmentServiceTest.
 */
class SubmissionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2030-01-01T00:00:00Z");

    private SubmissionRepository submissionRepository;
    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionRepository = mock(SubmissionRepository.class);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        submissionService = new SubmissionService(submissionRepository, fixedClock);
    }

    @Test
    void createPersistsSubmissionWithGeneratedIdTimestampsAndNormalizedText() {
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));
        List<Map<String, Object>> answers = List.of(Map.of("response", "Hello World"));

        Submission created = submissionService.create("assessment-1", answers, "user-1");

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        Submission saved = captor.getValue();

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getAssessmentId()).isEqualTo("assessment-1");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getAnswers()).isEqualTo(answers);
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_NOW);
        // "Hello World" -> lowercased, punctuation stripped, whitespace collapsed/trimmed.
        assertThat(saved.getNormalizedAnswerText()).isEqualTo("hello world");
        assertThat(created).isSameAs(saved);
    }

    @Test
    void createRejectsBlankAssessmentIdWithBadRequest() {
        List<Map<String, Object>> answers = List.of(Map.of("response", "text"));

        assertThatThrownBy(() -> submissionService.create("  ", answers, "user-1"))
                .isInstanceOf(BadRequestException.class);

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void createRejectsEmptyAnswersWithBadRequest() {
        assertThatThrownBy(() -> submissionService.create("assessment-1", List.of(), "user-1"))
                .isInstanceOf(BadRequestException.class);

        verify(submissionRepository, never()).save(any());
    }

    @Test
    void getByIdReturnsSubmissionWhenFound() {
        Submission existing = submissionOf("id-1", "assessment-1", "user-1", "hello world");
        when(submissionRepository.findById("id-1")).thenReturn(Optional.of(existing));

        Submission result = submissionService.getById("id-1");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        when(submissionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.getById("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * Known-input exact-score assertion: target text shares 3 of 5 total
     * distinct tokens with candidate A ("alpha beta gamma" intersect,
     * "delta"/"epsilon" only in one side each) => 3/5 = 0.6 exactly, and
     * shares zero tokens with candidate B => 0.0 exactly. This directly
     * verifies the relocated jaccard()/round2() algorithm, not just "some
     * number".
     */
    @Test
    void computeSimilarityProducesExactJaccardScoresForKnownCandidates() {
        Submission target = submissionOf("target-id", "assessment-1", "user-1", "alpha beta gamma delta");
        Submission candidateA = submissionOf("candidate-a", "assessment-1", "user-2", "alpha beta gamma epsilon");
        Submission candidateB = submissionOf("candidate-b", "assessment-1", "user-3", "zeta eta theta iota");

        when(submissionRepository.findById("target-id")).thenReturn(Optional.of(target));
        when(submissionRepository.findByAssessmentIdAndIdNot("assessment-1", "target-id"))
                .thenReturn(List.of(candidateA, candidateB));

        SimilarityResult result = submissionService.computeSimilarity("target-id");

        assertThat(result.submissionId()).isEqualTo("target-id");
        assertThat(result.assessmentId()).isEqualTo("assessment-1");
        assertThat(result.comparedSubmissions()).isEqualTo(2);
        assertThat(result.maxSimilarity()).isEqualTo(0.6);
        assertThat(result.riskLevel()).isEqualTo("MEDIUM");
        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches().get(0).submissionId()).isEqualTo("candidate-a");
        assertThat(result.matches().get(0).similarityScore()).isEqualTo(0.6);
        assertThat(result.matches().get(1).submissionId()).isEqualTo("candidate-b");
        assertThat(result.matches().get(1).similarityScore()).isEqualTo(0.0);
    }

    @Test
    void computeSimilarityWithZeroCandidatesReturnsEmptyMatchesAndLowRisk() {
        Submission target = submissionOf("target-id", "assessment-1", "user-1", "alpha beta gamma delta");
        when(submissionRepository.findById("target-id")).thenReturn(Optional.of(target));
        when(submissionRepository.findByAssessmentIdAndIdNot("assessment-1", "target-id"))
                .thenReturn(List.of());

        SimilarityResult result = submissionService.computeSimilarity("target-id");

        assertThat(result.matches()).isEmpty();
        assertThat(result.comparedSubmissions()).isEqualTo(0);
        assertThat(result.maxSimilarity()).isEqualTo(0.0);
        assertThat(result.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void computeSimilarityThrowsNotFoundForUnknownSubmission() {
        when(submissionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.computeSimilarity("missing"))
                .isInstanceOf(NotFoundException.class);

        verify(submissionRepository, never()).findByAssessmentIdAndIdNot(any(), any());
    }

    private static Submission submissionOf(String id, String assessmentId, String userId, String normalizedAnswerText) {
        return new Submission(
                id, assessmentId, userId,
                List.of(Map.of("response", normalizedAnswerText)),
                normalizedAnswerText,
                SubmissionStatus.SUBMITTED,
                FIXED_NOW, FIXED_NOW);
    }
}
