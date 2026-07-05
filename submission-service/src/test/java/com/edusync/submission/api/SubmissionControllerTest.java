package com.edusync.submission.api;

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

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration tests (real Spring context, real H2 database, real
 * Flyway migration) covering the endpoints the audit found completely
 * untested: missing-header handling, the INSTRUCTOR/ADMIN-only similarity
 * gate, and a full create -> similarity happy path backed by real
 * persistence, mirroring CourseControllerTest/EnrollmentControllerTest.
 *
 * @Transactional rolls back each test's DB writes so tests do not leak state
 * into one another despite sharing one Spring context / H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/submissions/health")).andExpect(status().isOk());
    }

    @Test
    void createWithoutUserIdHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assessmentId":"a-1","answers":[{"questionId":"q1","response":"hello"}]}
                                """))
                // no X-User-Id header at all
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void createRejectsEmptyAnswersWithValidationError() throws Exception {
        mockMvc.perform(post("/submissions")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assessmentId":"a-1","answers":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getReturnsNotFoundForUnknownSubmission() throws Exception {
        mockMvc.perform(get("/submissions/{id}", "does-not-exist")
                        .header("X-User-Id", "u-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void similarityWithoutInstructorOrAdminRoleReturnsForbidden() throws Exception {
        String id = createSubmission("u-1", "a-1", "Dynamic programming uses overlapping subproblems");

        mockMvc.perform(get("/submissions/{id}/similarity", id)
                        .header("X-User-Id", "u-1")
                        .header("X-User-Roles", "STUDENT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void similarityWithNoRolesHeaderAtAllReturnsForbidden() throws Exception {
        String id = createSubmission("u-1", "a-1", "Dynamic programming uses overlapping subproblems");

        mockMvc.perform(get("/submissions/{id}/similarity", id)
                        .header("X-User-Id", "u-1"))
                // no X-User-Roles header -> no roles -> requireRole fails
                .andExpect(status().isForbidden());
    }

    @Test
    void createThenSimilarityHappyPathFindsMatchAgainstSecondSubmission() throws Exception {
        String aId = createSubmission("u-1", "a-1", "Dynamic programming uses overlapping subproblems");
        createSubmission("u-2", "a-1", "Dynamic programming solves overlapping subproblems efficiently");

        mockMvc.perform(get("/submissions/{id}/similarity", aId)
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionId").value(aId))
                .andExpect(jsonPath("$.assessmentId").value("a-1"))
                .andExpect(jsonPath("$.comparedSubmissions").value(1))
                .andExpect(jsonPath("$.matches[0].userId").value("u-2"))
                .andExpect(jsonPath("$.maxSimilarity").isNumber())
                .andExpect(jsonPath("$.riskLevel").isString());
    }

    private String createSubmission(String userId, String assessmentId, String responseText) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "assessmentId", assessmentId,
                "answers", List.of(Map.of("questionId", "q1", "response", responseText))));

        MvcResult result = mockMvc.perform(post("/submissions")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }
}
