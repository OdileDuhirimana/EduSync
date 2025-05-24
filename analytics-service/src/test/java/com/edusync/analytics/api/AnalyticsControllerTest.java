package com.edusync.analytics.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/analytics/health")).andExpect(status().isOk());
    }

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetFinalGrade").value(85.0))
                .andExpect(jsonPath("$.requiredAverageOnRemaining").exists());
    }
}
