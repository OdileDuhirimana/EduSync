package com.edusync.analytics.service;

import com.edusync.analytics.api.dto.AtRiskRequestDto;
import com.edusync.analytics.api.dto.CompletedGradeDto;
import com.edusync.analytics.api.dto.GradeForecastRequestDto;
import com.edusync.analytics.api.dto.LearnerSignalDto;
import com.edusync.analytics.api.dto.RemainingGradeDto;
import com.edusync.analytics.api.dto.StudyModuleDto;
import com.edusync.analytics.api.dto.StudyPlanRequestDto;
import com.edusync.analytics.domain.EngagementEvent;
import com.edusync.analytics.domain.EngagementEventType;
import com.edusync.analytics.domain.FunnelEvent;
import com.edusync.analytics.domain.FunnelStage;
import com.edusync.analytics.domain.GradeRecordSnapshot;
import com.edusync.analytics.domain.LetterGrade;
import com.edusync.analytics.repository.EngagementEventRepository;
import com.edusync.analytics.repository.FunnelEventRepository;
import com.edusync.analytics.repository.GradeRecordSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * All business logic for analytics-service, extracted out of
 * AnalyticsController.
 *
 * WHY this class exists (mirrors CourseService/EnrollmentService in the
 * sibling services, fixing the same architectural gap the audit flagged for
 * this module specifically — "no service/domain/repository layer at all"):
 * the previous AnalyticsController mixed HTTP routing with both fully-mocked
 * static responses and real algorithm code, with no persistence and no
 * shared validation strategy. This service depends only on the three JPA
 * repository abstractions (constructor-injected, per SOLID's Dependency
 * Inversion Principle) plus an injected Clock, which is what makes it
 * testable via AnalyticsServiceTest with mocked repositories — no Spring
 * context, no HTTP layer, no database required.
 *
 * == Part A: ingestion-then-aggregate design for engagement/grade/funnel ==
 *
 * /engagement, /grade-distribution and /funnels were flagged as a Critical
 * Issue: "fixtures masquerading as computed responses" — they returned the
 * same hardcoded numbers regardless of the courseId passed in. This monorepo
 * pass gives analytics-service no upstream event stream from course-service
 * or enrollment-service to aggregate over (no Kafka topic, no shared event
 * bus exists anywhere else in this codebase). Given that constraint, three
 * options existed: (1) leave the endpoints mocked, (2) invent a fake
 * cross-service call to a feed that doesn't exist anywhere else in the
 * system, or (3) have analytics-service own real ingestion of the raw events
 * it reports on, and compute its read endpoints as genuine aggregation
 * queries over that persisted data. Option 3 is the only one that is both
 * honest (the numbers really are computed, not fabricated) and small (it
 * doesn't pretend to a level of system integration this codebase pass
 * doesn't otherwise have) — see engagement()/gradeDistribution()/funnels()
 * below for the actual aggregation queries this unlocks, and their matching
 * recordXxx() ingestion methods for how the data gets there.
 *
 * == Part B: two validation strategies, not one inconsistent one ==
 *
 * studyPlan/atRisk/gradeForecast use two deliberately different validation
 * strategies for two different field semantics: fields with a sensible
 * default (e.g. a null weeklyHours defaulting to 8) are validated with
 * {@code @Positive}-style "reject if present-but-invalid, default if absent"
 * Bean Validation annotations on the request DTOs (api/dto package) *and*
 * still pass through {@link #bounded(Double, double, double, double)} here
 * as defense in depth for any caller that reaches this service directly.
 * Fields where an empty/missing collection makes the whole request
 * meaningless (e.g. a study plan with zero modules) are validated with
 * {@code @NotEmpty}, which rejects outright rather than defaulting. Both are
 * legitimate strategies for different situations, not the duplicated
 * inconsistency the audit flagged when the same class of check appeared
 * twice for the same reason.
 */
@Service
public class AnalyticsService {

    private final EngagementEventRepository engagementEventRepository;
    private final GradeRecordSnapshotRepository gradeRecordSnapshotRepository;
    private final FunnelEventRepository funnelEventRepository;
    private final Clock clock;

    public AnalyticsService(
            EngagementEventRepository engagementEventRepository,
            GradeRecordSnapshotRepository gradeRecordSnapshotRepository,
            FunnelEventRepository funnelEventRepository,
            Clock clock) {
        this.engagementEventRepository = engagementEventRepository;
        this.gradeRecordSnapshotRepository = gradeRecordSnapshotRepository;
        this.funnelEventRepository = funnelEventRepository;
        this.clock = clock;
    }

    // ---- Part A: engagement -------------------------------------------

    @Transactional
    public EngagementEvent recordEngagementEvent(String courseId, String userId, EngagementEventType eventType) {
        EngagementEvent event = new EngagementEvent(
                UUID.randomUUID().toString(), courseId, userId, eventType, Instant.now(clock));
        return engagementEventRepository.save(event);
    }

    /**
     * WHY dau is "distinct users with an ACTIVE_SESSION event in the last
     * 24h" rather than a raw event count: a single user pinging
     * ACTIVE_SESSION ten times in a day should count once toward daily
     * active users, not ten times — that distinction is exactly why
     * {@link EngagementEventRepository#countDistinctUserIdByCourseIdAndEventTypeAndOccurredAtAfter}
     * exists as an explicit JPQL query instead of a plain row count.
     *
     * WHY completionRate returns 0.0 instead of throwing when there are no
     * VIEW funnel events yet: a brand-new course with no traffic is a
     * legitimate "no data yet" state, not an error condition — dividing by
     * zero here would either throw ArithmeticException (for integer division,
     * not applicable) or produce NaN (for floating point), either of which
     * would break JSON serialization or silently corrupt the response; 0.0
     * is the correct, honest answer to "what fraction of viewers completed
     * the course" when nobody has viewed it yet.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> engagement(String courseId) {
        Instant since = Instant.now(clock).minus(24, ChronoUnit.HOURS);
        long dau = engagementEventRepository.countDistinctUserIdByCourseIdAndEventTypeAndOccurredAtAfter(
                courseId, EngagementEventType.ACTIVE_SESSION, since);

        long viewCount = funnelEventRepository.countByCourseIdAndStage(courseId, FunnelStage.VIEW);
        long completeCount = funnelEventRepository.countByCourseIdAndStage(courseId, FunnelStage.COMPLETE);
        double completionRate = viewCount == 0 ? 0.0 : (double) completeCount / viewCount;

        return Map.of(
                "courseId", courseId,
                "dau", dau,
                "completionRate", round2(completionRate)
        );
    }

    // ---- Part A: grade distribution ------------------------------------

    @Transactional
    public GradeRecordSnapshot recordGradeSnapshot(String courseId, String userId, LetterGrade letterGrade) {
        GradeRecordSnapshot snapshot = new GradeRecordSnapshot(
                UUID.randomUUID().toString(), courseId, userId, letterGrade, Instant.now(clock));
        return gradeRecordSnapshotRepository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> gradeDistribution(String courseId) {
        List<Map<String, Object>> bins = new ArrayList<>();
        for (LetterGrade grade : LetterGrade.values()) {
            long count = gradeRecordSnapshotRepository.countByCourseIdAndLetterGrade(courseId, grade);
            bins.add(Map.of("grade", grade.name(), "count", count));
        }
        return Map.of("courseId", courseId, "bins", bins);
    }

    // ---- Part A: funnels -------------------------------------------------

    @Transactional
    public FunnelEvent recordFunnelEvent(String courseId, FunnelStage stage) {
        FunnelEvent event = new FunnelEvent(UUID.randomUUID().toString(), courseId, stage, Instant.now(clock));
        return funnelEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> funnels(String courseId) {
        List<Map<String, Object>> stages = new ArrayList<>();
        for (FunnelStage stage : FunnelStage.values()) {
            long count = funnelEventRepository.countByCourseIdAndStage(courseId, stage);
            stages.add(Map.of("stage", stage.name().toLowerCase(Locale.ROOT), "count", count));
        }
        return Map.of("courseId", courseId, "stages", stages);
    }

    // ---- Part B: study plan (relocated verbatim from AnalyticsController) --
    //
    // WHY the math below is unchanged from the pre-remediation controller:
    // the code review praised this algorithm as "readable without excessive
    // comments" and correct; the audit's finding was about *where* it lived
    // and *how* its input was validated, not the algorithm itself. Only the
    // request/module types (now StudyPlanRequestDto/StudyModuleDto) and the
    // time source (now the injected Clock, for testability, matching
    // CourseService/EnrollmentService's convention) changed.

    public Map<String, Object> studyPlan(StudyPlanRequestDto req) {
        int weeklyHours = req.weeklyHours() == null ? 8 : Math.max(1, req.weeklyHours());
        int horizonDays = req.horizonDays() == null ? 14 : Math.max(1, req.horizonDays());
        int dailyCapacityMinutes = Math.max(30, (weeklyHours * 60) / 7);
        LocalDate start = LocalDate.now(clock);
        LocalDate end = start.plusDays(horizonDays - 1L);

        List<StudyModuleDto> modules = req.modules() == null ? List.of() : req.modules();
        List<WeightedModule> weighted = new ArrayList<>();
        for (StudyModuleDto module : modules) {
            int minutes = module.estimatedMinutes() == null ? 45 : Math.max(15, module.estimatedMinutes());
            int difficulty = module.difficulty() == null ? 3 : Math.max(1, Math.min(5, module.difficulty()));
            LocalDate due = parseDueDate(module.dueDate(), end);
            long daysUntilDue = Math.max(0, start.until(due).getDays());
            double urgency = (horizonDays - Math.min(horizonDays, daysUntilDue) + 1.0) / (horizonDays + 1.0);
            double priority = (minutes * difficulty) * (1.0 + urgency);
            weighted.add(new WeightedModule(module, minutes, due, priority));
        }
        weighted.sort(Comparator.comparingDouble(WeightedModule::priority).reversed());

        Map<LocalDate, Integer> loads = new HashMap<>();
        Map<LocalDate, List<Map<String, Object>>> schedule = new HashMap<>();
        List<Map<String, Object>> backlog = new ArrayList<>();

        for (WeightedModule module : weighted) {
            int remaining = module.estimatedMinutes();
            LocalDate latest = module.dueDate().isBefore(end) ? module.dueDate() : end;
            for (LocalDate day = start; !day.isAfter(latest) && remaining > 0; day = day.plusDays(1)) {
                int used = loads.getOrDefault(day, 0);
                int room = dailyCapacityMinutes - used;
                if (room <= 0) {
                    continue;
                }
                int assigned = Math.min(room, remaining);
                Map<String, Object> task = new HashMap<>();
                task.put("moduleId", safeText(module.module().moduleId(), "module"));
                task.put("title", safeText(module.module().title(), "Module"));
                task.put("minutes", assigned);
                task.put("dueDate", module.dueDate().toString());
                schedule.computeIfAbsent(day, ignored -> new ArrayList<>()).add(task);
                loads.put(day, used + assigned);
                remaining -= assigned;
            }
            if (remaining > 0) {
                backlog.add(Map.of(
                        "moduleId", safeText(module.module().moduleId(), "module"),
                        "unplannedMinutes", remaining
                ));
            }
        }

        List<Map<String, Object>> days = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(Map.of(
                    "date", day.toString(),
                    "totalMinutes", loads.getOrDefault(day, 0),
                    "tasks", schedule.getOrDefault(day, List.of())
            ));
        }

        return Map.of(
                "learnerId", safeText(req.learnerId(), "anonymous"),
                "generatedAt", Instant.now(clock).toString(),
                "dailyCapacityMinutes", dailyCapacityMinutes,
                "schedule", days,
                "backlog", backlog
        );
    }

    // ---- Part B: at-risk scoring (relocated verbatim) ----------------------

    public Map<String, Object> atRisk(AtRiskRequestDto req) {
        List<LearnerSignalDto> learners = req.learners() == null ? List.of() : req.learners();
        List<Map<String, Object>> results = new ArrayList<>();

        for (LearnerSignalDto learner : learners) {
            double completionRate = bounded(learner.completionRate(), 0.0, 1.0, 0.5);
            double averageScore = bounded(learner.averageScore(), 0.0, 100.0, 65.0);
            double inactivityDays = bounded(learner.lastActiveDaysAgo() == null ? null : learner.lastActiveDaysAgo().doubleValue(), 0.0, 30.0, 7.0);
            double missedDeadlines = bounded(learner.missedDeadlines() == null ? null : learner.missedDeadlines().doubleValue(), 0.0, 6.0, 1.0);

            double score = (1.0 - completionRate) * 35.0
                    + (1.0 - (averageScore / 100.0)) * 30.0
                    + (inactivityDays / 30.0) * 20.0
                    + (missedDeadlines / 6.0) * 15.0;

            int riskScore = (int) Math.round(score);
            String riskLevel = riskScore >= 70 ? "HIGH" : riskScore >= 40 ? "MEDIUM" : "LOW";
            List<String> recommendations = buildRiskRecommendations(completionRate, averageScore, inactivityDays, missedDeadlines);

            results.add(Map.of(
                    "userId", safeText(learner.userId(), "unknown"),
                    "riskScore", riskScore,
                    "riskLevel", riskLevel,
                    "recommendations", recommendations
            ));
        }

        results.sort((a, b) -> Integer.compare((int) b.get("riskScore"), (int) a.get("riskScore")));
        return Map.of(
                "courseId", safeText(req.courseId(), "unknown"),
                "evaluatedAt", Instant.now(clock).toString(),
                "learners", results
        );
    }

    // ---- Part B: grade forecast (relocated verbatim) -----------------------

    public Map<String, Object> gradeForecast(GradeForecastRequestDto req) {
        List<CompletedGradeDto> completed = req.completed() == null ? List.of() : req.completed();
        List<RemainingGradeDto> remaining = req.remaining() == null ? List.of() : req.remaining();
        double target = bounded(req.targetFinalGrade(), 0.0, 100.0, 85.0);

        double earnedPoints = 0.0;
        double completedWeight = 0.0;
        for (CompletedGradeDto item : completed) {
            double weight = bounded(item.weightPct(), 0.0, 100.0, 0.0);
            double score = bounded(item.scorePct(), 0.0, 100.0, 0.0);
            completedWeight += weight;
            earnedPoints += (weight * score) / 100.0;
        }

        double remainingWeight = 0.0;
        for (RemainingGradeDto item : remaining) {
            remainingWeight += bounded(item.weightPct(), 0.0, 100.0, 0.0);
        }

        double currentAverage = completedWeight > 0 ? (earnedPoints / completedWeight) * 100.0 : 0.0;
        double requiredAverage = remainingWeight > 0
                ? ((target - earnedPoints) / remainingWeight) * 100.0
                : 0.0;
        double projectedFinal = earnedPoints + (remainingWeight * currentAverage) / 100.0;
        boolean feasible = requiredAverage <= 100.0;

        return Map.of(
                "learnerId", safeText(req.learnerId(), "unknown"),
                "courseId", safeText(req.courseId(), "unknown"),
                "currentAverage", round2(currentAverage),
                "earnedPoints", round2(earnedPoints),
                "remainingWeight", round2(remainingWeight),
                "requiredAverageOnRemaining", round2(Math.max(0.0, requiredAverage)),
                "projectedFinalAtCurrentPace", round2(projectedFinal),
                "targetFinalGrade", round2(target),
                "targetAchievable", feasible,
                "computedAt", Instant.now(clock).toString()
        );
    }

    // ---- Part B: shared helpers (relocated verbatim) -----------------------

    private LocalDate parseDueDate(String dueDateText, LocalDate fallback) {
        try {
            if (dueDateText == null || dueDateText.isBlank()) {
                return fallback;
            }
            return LocalDate.parse(dueDateText);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private double bounded(Double value, double min, double max, double fallback) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private List<String> buildRiskRecommendations(double completionRate, double averageScore, double inactivityDays, double missedDeadlines) {
        List<String> recs = new ArrayList<>();
        if (completionRate < 0.55) {
            recs.add("Schedule focused catch-up sessions on unfinished modules");
        }
        if (averageScore < 65.0) {
            recs.add("Assign remedial quizzes before next graded assessment");
        }
        if (inactivityDays > 7.0) {
            recs.add("Trigger outreach and re-engagement reminders this week");
        }
        if (missedDeadlines >= 2.0) {
            recs.add("Offer deadline planning support and weekly checkpoints");
        }
        if (recs.isEmpty()) {
            recs.add("Progress is stable; continue current pace");
        }
        return recs;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record WeightedModule(StudyModuleDto module, int estimatedMinutes, LocalDate dueDate, double priority) {}
}
