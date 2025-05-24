package com.edusync.analytics.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "timestamp", Instant.now().toString());
    }

    @GetMapping("/engagement")
    public Map<String, Object> engagement(@RequestParam String courseId) {
        return Map.of(
                "courseId", courseId,
                "dau", 42,
                "completionRate", 0.68
        );
    }

    @GetMapping("/grade-distribution")
    public Map<String, Object> gradeDistribution(@RequestParam String courseId) {
        return Map.of(
                "courseId", courseId,
                "bins", List.of(
                        Map.of("grade", "A", "count", 5),
                        Map.of("grade", "B", "count", 8),
                        Map.of("grade", "C", "count", 3)
                )
        );
    }

    @GetMapping("/funnels")
    public Map<String, Object> funnels(@RequestParam String courseId) {
        return Map.of(
                "courseId", courseId,
                "stages", List.of(
                        Map.of("stage", "view", "count", 100),
                        Map.of("stage", "enroll", "count", 40),
                        Map.of("stage", "complete", "count", 25)
                )
        );
    }

    public record StudyPlanRequest(String learnerId, Integer weeklyHours, Integer horizonDays, List<StudyModule> modules) {}
    public record StudyModule(String moduleId, String title, Integer estimatedMinutes, Integer difficulty, String dueDate) {}

    @PostMapping("/study-plan")
    public Map<String, Object> studyPlan(@RequestBody StudyPlanRequest req) {
        int weeklyHours = req.weeklyHours() == null ? 8 : Math.max(1, req.weeklyHours());
        int horizonDays = req.horizonDays() == null ? 14 : Math.max(1, req.horizonDays());
        int dailyCapacityMinutes = Math.max(30, (weeklyHours * 60) / 7);
        LocalDate start = LocalDate.now(ZoneOffset.UTC);
        LocalDate end = start.plusDays(horizonDays - 1L);

        List<StudyModule> modules = req.modules() == null ? List.of() : req.modules();
        List<WeightedModule> weighted = new ArrayList<>();
        for (StudyModule module : modules) {
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
                "generatedAt", Instant.now().toString(),
                "dailyCapacityMinutes", dailyCapacityMinutes,
                "schedule", days,
                "backlog", backlog
        );
    }

    public record AtRiskRequest(String courseId, List<LearnerSignal> learners) {}
    public record LearnerSignal(String userId, Double completionRate, Double averageScore, Integer lastActiveDaysAgo, Integer missedDeadlines) {}

    @PostMapping("/at-risk")
    public Map<String, Object> atRisk(@RequestBody AtRiskRequest req) {
        List<LearnerSignal> learners = req.learners() == null ? List.of() : req.learners();
        List<Map<String, Object>> results = new ArrayList<>();

        for (LearnerSignal learner : learners) {
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
                "evaluatedAt", Instant.now().toString(),
                "learners", results
        );
    }

    public record GradeForecastRequest(String learnerId, String courseId, List<CompletedGrade> completed, List<RemainingGrade> remaining, Double targetFinalGrade) {}
    public record CompletedGrade(String name, Double weightPct, Double scorePct) {}
    public record RemainingGrade(String name, Double weightPct) {}

    @PostMapping("/grade-forecast")
    public Map<String, Object> gradeForecast(@RequestBody GradeForecastRequest req) {
        List<CompletedGrade> completed = req.completed() == null ? List.of() : req.completed();
        List<RemainingGrade> remaining = req.remaining() == null ? List.of() : req.remaining();
        double target = bounded(req.targetFinalGrade(), 0.0, 100.0, 85.0);

        double earnedPoints = 0.0;
        double completedWeight = 0.0;
        for (CompletedGrade item : completed) {
            double weight = bounded(item.weightPct(), 0.0, 100.0, 0.0);
            double score = bounded(item.scorePct(), 0.0, 100.0, 0.0);
            completedWeight += weight;
            earnedPoints += (weight * score) / 100.0;
        }

        double remainingWeight = 0.0;
        for (RemainingGrade item : remaining) {
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
                "computedAt", Instant.now().toString()
        );
    }

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

    private record WeightedModule(StudyModule module, int estimatedMinutes, LocalDate dueDate, double priority) {}
}
