package com.edusync.analytics.service;

import com.edusync.analytics.api.dto.AtRiskRequestDto;
import com.edusync.analytics.api.dto.CompletedGradeDto;
import com.edusync.analytics.api.dto.GradeForecastRequestDto;
import com.edusync.analytics.api.dto.LearnerSignalDto;
import com.edusync.analytics.api.dto.RemainingGradeDto;
import com.edusync.analytics.api.dto.StudyModuleDto;
import com.edusync.analytics.api.dto.StudyPlanRequestDto;
import com.edusync.analytics.domain.EngagementEventType;
import com.edusync.analytics.domain.FunnelStage;
import com.edusync.analytics.domain.LetterGrade;
import com.edusync.analytics.repository.EngagementEventRepository;
import com.edusync.analytics.repository.FunnelEventRepository;
import com.edusync.analytics.repository.GradeRecordSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * True unit tests for AnalyticsService: all three repository collaborators
 * are Mockito mocks and Clock is fixed, so these tests exercise both the
 * real aggregation queries (Part A) and the relocated scheduling/scoring/
 * forecasting algorithms (Part B) in complete isolation from Spring, HTTP,
 * and the database — the same isolation level as CourseServiceTest/
 * EnrollmentServiceTest in the sibling services, and the class of test the
 * audit found completely absent for this module ("no service layer at all").
 */
class AnalyticsServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2030-01-01T00:00:00Z");

    private EngagementEventRepository engagementEventRepository;
    private GradeRecordSnapshotRepository gradeRecordSnapshotRepository;
    private FunnelEventRepository funnelEventRepository;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        engagementEventRepository = mock(EngagementEventRepository.class);
        gradeRecordSnapshotRepository = mock(GradeRecordSnapshotRepository.class);
        funnelEventRepository = mock(FunnelEventRepository.class);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        analyticsService = new AnalyticsService(
                engagementEventRepository, gradeRecordSnapshotRepository, funnelEventRepository, fixedClock);
    }

    // ---- Part A: engagement -------------------------------------------

    @Test
    void engagementComputesDauFromDistinctActiveSessionUsersAndRealCompletionRate() {
        when(engagementEventRepository.countDistinctUserIdByCourseIdAndEventTypeAndOccurredAtAfter(
                eq("course-1"), eq(EngagementEventType.ACTIVE_SESSION), any(Instant.class)))
                .thenReturn(3L);
        when(funnelEventRepository.countByCourseIdAndStage("course-1", FunnelStage.VIEW)).thenReturn(10L);
        when(funnelEventRepository.countByCourseIdAndStage("course-1", FunnelStage.COMPLETE)).thenReturn(4L);

        Map<String, Object> result = analyticsService.engagement("course-1");

        assertThat(result.get("courseId")).isEqualTo("course-1");
        assertThat(result.get("dau")).isEqualTo(3L);
        assertThat(result.get("completionRate")).isEqualTo(0.4);
        verify(engagementEventRepository).countDistinctUserIdByCourseIdAndEventTypeAndOccurredAtAfter(
                eq("course-1"), eq(EngagementEventType.ACTIVE_SESSION), eq(FIXED_NOW.minusSeconds(24 * 3600)));
    }

    @Test
    void engagementReturnsZeroCompletionRateWhenNoViewEventsExistYet() {
        when(engagementEventRepository.countDistinctUserIdByCourseIdAndEventTypeAndOccurredAtAfter(
                any(), any(), any())).thenReturn(0L);
        when(funnelEventRepository.countByCourseIdAndStage("new-course", FunnelStage.VIEW)).thenReturn(0L);
        when(funnelEventRepository.countByCourseIdAndStage("new-course", FunnelStage.COMPLETE)).thenReturn(0L);

        Map<String, Object> result = analyticsService.engagement("new-course");

        assertThat(result.get("completionRate")).isEqualTo(0.0);
    }

    // ---- Part A: grade distribution -------------------------------------

    @Test
    void gradeDistributionReturnsRealCountsPerLetterGradeFromRepository() {
        when(gradeRecordSnapshotRepository.countByCourseIdAndLetterGrade("course-1", LetterGrade.A)).thenReturn(2L);
        when(gradeRecordSnapshotRepository.countByCourseIdAndLetterGrade("course-1", LetterGrade.B)).thenReturn(5L);
        when(gradeRecordSnapshotRepository.countByCourseIdAndLetterGrade("course-1", LetterGrade.C)).thenReturn(1L);
        when(gradeRecordSnapshotRepository.countByCourseIdAndLetterGrade("course-1", LetterGrade.D)).thenReturn(0L);
        when(gradeRecordSnapshotRepository.countByCourseIdAndLetterGrade("course-1", LetterGrade.F)).thenReturn(0L);

        Map<String, Object> result = analyticsService.gradeDistribution("course-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bins = (List<Map<String, Object>>) result.get("bins");
        assertThat(bins).containsExactly(
                Map.of("grade", "A", "count", 2L),
                Map.of("grade", "B", "count", 5L),
                Map.of("grade", "C", "count", 1L),
                Map.of("grade", "D", "count", 0L),
                Map.of("grade", "F", "count", 0L));
    }

    // ---- Part A: funnels --------------------------------------------------

    @Test
    void funnelsReturnsRealCountsPerStageFromRepository() {
        when(funnelEventRepository.countByCourseIdAndStage("course-1", FunnelStage.VIEW)).thenReturn(100L);
        when(funnelEventRepository.countByCourseIdAndStage("course-1", FunnelStage.ENROLL)).thenReturn(40L);
        when(funnelEventRepository.countByCourseIdAndStage("course-1", FunnelStage.COMPLETE)).thenReturn(25L);

        Map<String, Object> result = analyticsService.funnels("course-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) result.get("stages");
        assertThat(stages).containsExactly(
                Map.of("stage", "view", "count", 100L),
                Map.of("stage", "enroll", "count", 40L),
                Map.of("stage", "complete", "count", 25L));
    }

    // ---- Part B: study plan -------------------------------------------------

    @Test
    void studyPlanPacksModulesGreedilyByPriorityWithinDailyCapacity() {
        StudyPlanRequestDto request = new StudyPlanRequestDto(
                "u-1", 7, 5,
                List.of(
                        new StudyModuleDto("m1", "Recursion", 120, 4, "2030-01-03"),
                        new StudyModuleDto("m2", "Graphs", 90, 5, "2030-01-05")));

        Map<String, Object> result = analyticsService.studyPlan(request);

        assertThat(result.get("learnerId")).isEqualTo("u-1");
        // weeklyHours=7 -> dailyCapacityMinutes = max(30, (7*60)/7) = 60 exactly.
        assertThat(result.get("dailyCapacityMinutes")).isEqualTo(60);
        assertThat((List<?>) result.get("backlog")).isEmpty();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule = (List<Map<String, Object>>) result.get("schedule");
        assertThat(schedule).hasSize(5);
        // Day 1 (2030-01-01): the higher-priority, sooner-due module (m1) is
        // scheduled first and fills the entire daily capacity.
        assertThat(schedule.get(0).get("date")).isEqualTo("2030-01-01");
        assertThat(schedule.get(0).get("totalMinutes")).isEqualTo(60);
    }

    // ---- Part B: at-risk scoring --------------------------------------------

    @Test
    void atRiskComputesExactWeightedScoreAndSortsHighestFirst() {
        AtRiskRequestDto request = new AtRiskRequestDto(
                "c-1",
                List.of(new LearnerSignalDto("u-risk", 0.05, 30.0, 30, 6)));

        Map<String, Object> result = analyticsService.atRisk(request);

        assertThat(result.get("courseId")).isEqualTo("c-1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> learners = (List<Map<String, Object>>) result.get("learners");
        // score = (1-0.05)*35 + (1-0.30)*30 + (30/30)*20 + (6/6)*15 = 89.25 -> rounds to 89.
        assertThat(learners.get(0).get("riskScore")).isEqualTo(89);
        assertThat(learners.get(0).get("riskLevel")).isEqualTo("HIGH");
    }

    // ---- Part B: grade forecast ---------------------------------------------

    @Test
    void gradeForecastComputesExactRequiredAverageAndFeasibility() {
        GradeForecastRequestDto request = new GradeForecastRequestDto(
                "u-1", "c-1",
                List.of(new CompletedGradeDto("Quiz 1", 30.0, 80.0)),
                List.of(new RemainingGradeDto("Final Exam", 70.0)),
                85.0);

        Map<String, Object> result = analyticsService.gradeForecast(request);

        assertThat(result.get("currentAverage")).isEqualTo(80.0);
        // requiredAverage = ((85 - 24) / 70) * 100 = 87.142857... -> round2 = 87.14
        assertThat(result.get("requiredAverageOnRemaining")).isEqualTo(87.14);
        assertThat(result.get("projectedFinalAtCurrentPace")).isEqualTo(80.0);
        assertThat(result.get("targetAchievable")).isEqualTo(true);
    }
}
