package com.edusync.grading.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests (real Spring context, real H2 database, real
 * Flyway migration) covering the endpoints the audits found completely
 * untested/unauthorized: role checks on grading/publish/regrade-decision,
 * validation failures, and the full auto-grade -> publish -> regrade-request
 * -> regrade-decision happy path. Mirrors CourseControllerTest/
 * EnrollmentControllerTest's style.
 *
 * @Transactional rolls back each test's DB writes so tests do not leak state
 * into one another despite sharing one Spring context / H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GradingControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/grading/health")).andExpect(status().isOk());
    }

    @Test
    void autoGradeRequiresInstructorRole() throws Exception {
        mockMvc.perform(post("/grading/auto/{submissionId}", "sub-forbidden-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAutoGradePayload()))
                // no X-User-Roles header at all
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void manualGradeRequiresInstructorRole() throws Exception {
        mockMvc.perform(post("/grading/manual/{submissionId}", "sub-forbidden-2")
                        .header("X-User-Roles", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"breakdown":{"q1":30,"q2":40},"feedback":"Good effort"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void publishRequiresInstructorRole() throws Exception {
        mockMvc.perform(post("/grading/{submissionId}/publish", "sub-forbidden-3")
                        .header("X-User-Roles", "STUDENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void regradeDecisionRequiresInstructorRole() throws Exception {
        mockMvc.perform(post("/grading/regrade/{requestId}/decision", "does-not-exist")
                        .header("X-User-Roles", "STUDENT")
                        .header("X-User-Id", "student-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void manualGradeRejectsEmptyBreakdownWithValidationError() throws Exception {
        mockMvc.perform(post("/grading/manual/{submissionId}", "sub-invalid-1")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"breakdown":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void autoGradeRejectsMissingAnswerKeyWithValidationError() throws Exception {
        mockMvc.perform(post("/grading/auto/{submissionId}", "sub-invalid-2")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":[{"questionId":"q1","response":"a"}],"answerKey":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void regradeRequestWithoutUserIdHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/grading/regrade/{submissionId}/request", "sub-no-header")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"please review"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void publishReturnsNotFoundWhenSubmissionHasNoGrade() throws Exception {
        mockMvc.perform(post("/grading/{submissionId}/publish", "sub-ungraded")
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void regradeDecisionOnUnknownRequestIdReturnsNotFound() throws Exception {
        mockMvc.perform(post("/grading/regrade/{requestId}/decision", "does-not-exist")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .header("X-User-Id", "instructor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void fullHappyPathAutoGradePublishRegradeRequestAndDecision() throws Exception {
        String submissionId = "sub-happy-1";

        // 1. Auto-grade: q1 matches (case-insensitive/trimmed), q2 does not.
        // total = round((60 / 100) * 100) = 60.
        MvcResult autoGradeResult = mockMvc.perform(post("/grading/auto/{submissionId}", submissionId)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAutoGradePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(60))
                .andExpect(jsonPath("$.status").value("GRADED"))
                .andReturn();
        JsonNode autoGraded = objectMapper.readTree(autoGradeResult.getResponse().getContentAsString());
        assertThat(autoGraded.get("submissionId").asText()).isEqualTo(submissionId);

        // 2. Publish requires an existing grade and returns it.
        mockMvc.perform(post("/grading/{submissionId}/publish", submissionId)
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(60));

        // 3. The student requests a regrade of their own submission.
        MvcResult requestResult = mockMvc.perform(post("/grading/regrade/{submissionId}/request", submissionId)
                        .header("X-User-Id", "student-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Please review rubric interpretation on q2"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedBy").value("student-1"))
                .andReturn();
        JsonNode requestJson = objectMapper.readTree(requestResult.getResponse().getContentAsString());
        String requestId = requestJson.get("requestId").asText();

        // 4. An instructor approves the regrade with an override total.
        mockMvc.perform(post("/grading/regrade/{requestId}/decision", requestId)
                        .header("X-User-Roles", "INSTRUCTOR")
                        .header("X-User-Id", "instructor-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","note":"Accepted","overrideTotal":85}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("instructor-1"))
                .andExpect(jsonPath("$.currentGrade.total").value(85))
                .andExpect(jsonPath("$.currentGrade.status").value("GRADED_OVERRIDDEN"));

        // 5. GET reflects the decided case and the overridden grade.
        mockMvc.perform(get("/grading/regrade/{requestId}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.currentGrade.total").value(85));
    }

    private String validAutoGradePayload() {
        return """
                {
                  "answers": [
                    {"questionId":"q1","response":"A"},
                    {"questionId":"q2","response":"wrong"}
                  ],
                  "answerKey": [
                    {"questionId":"q1","correctAnswer":"a","points":60},
                    {"questionId":"q2","correctAnswer":"b","points":40}
                  ]
                }
                """;
    }
}
