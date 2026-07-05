package com.edusync.analytics.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests (real Spring context, real H2 database, real
 * Flyway migration) covering what the audit found missing: authorization on
 * the instructor-only aggregate/ingestion endpoints, Bean Validation on the
 * self-service planning endpoints, and — most importantly — a full
 * ingest-then-read flow proving /engagement, /grade-distribution and
 * /funnels compute real numbers from persisted data instead of returning the
 * previous hardcoded fixtures.
 *
 * @Transactional rolls back each test's DB writes so tests do not leak state
 * into one another despite sharing one Spring context / H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/analytics/health")).andExpect(status().isOk());
    }

    // ---- Authorization ------------------------------------------------

    @Test
    void engagementRequiresInstructorOrAdminRole() throws Exception {
        mockMvc.perform(get("/analytics/engagement").param("courseId", "c-1"))
                // no X-User-Roles header at all
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void studentCannotReadEngagement() throws Exception {
        mockMvc.perform(get("/analytics/engagement")
                        .param("courseId", "c-1")
                        .header("X-User-Roles", "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void atRiskRequiresInstructorOrAdminRole() throws Exception {
        String payload = """
                {
                  "courseId": "c-1",
                  "learners": [
                    {"userId":"u-risk","completionRate":0.05,"averageScore":30,"lastActiveDaysAgo":30,"missedDeadlines":6}
                  ]
                }
                """;
        mockMvc.perform(post("/analytics/at-risk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                // no X-User-Roles header: rejected before it ever scores a batch of other learners
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void engagementIngestionRequiresInstructorOrAdminRole() throws Exception {
        mockMvc.perform(post("/analytics/events/engagement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"c-1","userId":"u-1","eventType":"ACTIVE_SESSION"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void studyPlanWithoutUserIdHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/analytics/study-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weeklyHours":7,"horizonDays":5,"modules":[
                                  {"moduleId":"m1","title":"Recursion","estimatedMinutes":120,"difficulty":4,"dueDate":"2030-01-03"}
                                ]}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---- Validation -----------------------------------------------------

    @Test
    void studyPlanRejectsNonPositiveWeeklyHours() throws Exception {
        mockMvc.perform(post("/analytics/study-plan")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weeklyHours":0,"horizonDays":5,"modules":[
                                  {"moduleId":"m1","title":"Recursion","estimatedMinutes":120,"difficulty":4,"dueDate":"2030-01-03"}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void studyPlanRejectsEmptyModuleList() throws Exception {
        mockMvc.perform(post("/analytics/study-plan")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"learnerId":"u-1","weeklyHours":7,"horizonDays":5,"modules":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void gradeForecastRejectsOutOfRangeTargetFinalGrade() throws Exception {
        mockMvc.perform(post("/analytics/grade-forecast")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "learnerId":"u-1",
                                  "courseId":"c-1",
                                  "completed":[{"name":"Quiz 1","weightPct":30,"scorePct":80}],
                                  "remaining":[{"name":"Final Exam","weightPct":70}],
                                  "targetFinalGrade":150
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void atRiskRejectsOutOfRangeCompletionRate() throws Exception {
        mockMvc.perform(post("/analytics/at-risk")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseId": "c-1",
                                  "learners": [
                                    {"userId":"u-risk","completionRate":4.0,"averageScore":30,"lastActiveDaysAgo":30,"missedDeadlines":6}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- Part B happy paths (algorithms unchanged, now behind DTOs) -------

    @Test
    void studyPlanShouldGenerateSchedule() throws Exception {
        String payload = """
                {
                  "learnerId": "u-1",
                  "weeklyHours": 7,
                  "horizonDays": 5,
                  "modules": [
                    {"moduleId":"m1","title":"Recursion","estimatedMinutes":120,"difficulty":4,"dueDate":"2030-01-03"},
                    {"moduleId":"m2","title":"Graphs","estimatedMinutes":90,"difficulty":5,"dueDate":"2030-01-05"}
                  ]
                }
                """;
        mockMvc.perform(post("/analytics/study-plan")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerId").value("u-1"))
                .andExpect(jsonPath("$.schedule").isArray());
    }

    @Test
    void atRiskShouldScoreLearners() throws Exception {
        String payload = """
                {
                  "courseId": "c-1",
                  "learners": [
                    {"userId":"u-risk","completionRate":0.05,"averageScore":30,"lastActiveDaysAgo":30,"missedDeadlines":6}
                  ]
                }
                """;
        mockMvc.perform(post("/analytics/at-risk")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value("c-1"))
                .andExpect(jsonPath("$.learners[0].riskLevel").value("HIGH"));
    }

    @Test
    void gradeForecastShouldReturnFeasibility() throws Exception {
        String payload = """
                {
                  "learnerId":"u-1",
                  "courseId":"c-1",
                  "completed":[{"name":"Quiz 1","weightPct":30,"scorePct":80}],
                  "remaining":[{"name":"Final Exam","weightPct":70}],
                  "targetFinalGrade":85
                }
                """;
        mockMvc.perform(post("/analytics/grade-forecast")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetFinalGrade").value(85.0))
                .andExpect(jsonPath("$.requiredAverageOnRemaining").exists());
    }

    // ---- Part A: full ingest -> aggregate-read flow (proves real numbers) --

    @Test
    void engagementReflectsExactlyTheIngestedDistinctActiveSessionUsers() throws Exception {
        mockMvc.perform(post("/analytics/events/engagement")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-dau","userId":"user-a","eventType":"ACTIVE_SESSION"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/analytics/events/engagement")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-dau","userId":"user-b","eventType":"ACTIVE_SESSION"}
                                """))
                .andExpect(status().isCreated());
        // A VIEW event (not ACTIVE_SESSION) for the same course must not inflate dau.
        mockMvc.perform(post("/analytics/events/engagement")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-dau","userId":"user-c","eventType":"VIEW"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/analytics/engagement")
                        .param("courseId", "course-dau")
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value("course-dau"))
                .andExpect(jsonPath("$.dau").value(2));
    }

    @Test
    void gradeDistributionReflectsExactlyTheIngestedSnapshots() throws Exception {
        mockMvc.perform(post("/analytics/events/grade")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-grades","userId":"user-a","letterGrade":"A"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/analytics/events/grade")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-grades","userId":"user-b","letterGrade":"A"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/analytics/events/grade")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-grades","userId":"user-c","letterGrade":"B"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/analytics/grade-distribution")
                        .param("courseId", "course-grades")
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bins[0].grade").value("A"))
                .andExpect(jsonPath("$.bins[0].count").value(2))
                .andExpect(jsonPath("$.bins[1].grade").value("B"))
                .andExpect(jsonPath("$.bins[1].count").value(1));
    }

    @Test
    void funnelsReflectsExactlyTheIngestedStageCounts() throws Exception {
        mockMvc.perform(post("/analytics/events/funnel")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-funnel","stage":"VIEW"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/analytics/events/funnel")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-funnel","stage":"VIEW"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/analytics/events/funnel")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-funnel","stage":"ENROLL"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/analytics/funnels")
                        .param("courseId", "course-funnel")
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].stage").value("view"))
                .andExpect(jsonPath("$.stages[0].count").value(2))
                .andExpect(jsonPath("$.stages[1].stage").value("enroll"))
                .andExpect(jsonPath("$.stages[1].count").value(1))
                .andExpect(jsonPath("$.stages[2].stage").value("complete"))
                .andExpect(jsonPath("$.stages[2].count").value(0));
    }
}
