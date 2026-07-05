package com.edusync.assessment.api;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests (real Spring context, real H2 database, real
 * Flyway migration) covering the endpoints the audit found completely
 * untested and unauthorized: create's role requirement, get/start's
 * required-header enforcement, and the create -> start -> start-again
 * idempotent-token flow.
 *
 * @Transactional rolls back each test's DB writes so tests do not leak
 * state into one another despite sharing one Spring context / H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/assessments/health")).andExpect(status().isOk());
    }

    @Test
    void createRequiresInstructorRole() throws Exception {
        mockMvc.perform(post("/assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-1","title":"Midterm"}
                                """))
                // no X-User-Roles header at all
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void studentCannotCreateAssessment() throws Exception {
        mockMvc.perform(post("/assessments")
                        .header("X-User-Roles", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-1","title":"Midterm"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRejectsBlankTitleWithValidationError() throws Exception {
        mockMvc.perform(post("/assessments")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-1","title":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsUnknownTypeWithValidationError() throws Exception {
        mockMvc.perform(post("/assessments")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"course-1","title":"Midterm","type":"BOGUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getRequiresUserIdHeader() throws Exception {
        mockMvc.perform(get("/assessments/{id}", "does-not-exist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void getReturnsNotFoundForUnknownAssessment() throws Exception {
        mockMvc.perform(get("/assessments/{id}", "does-not-exist")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void startRequiresUserIdHeader() throws Exception {
        String id = createAssessment("Quiz 1", null);

        mockMvc.perform(post("/assessments/{id}/start", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void startReturnsNotFoundForUnknownAssessment() throws Exception {
        mockMvc.perform(post("/assessments/{id}/start", "does-not-exist")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void fullHappyPathCreateGetStartAndStartAgainIsIdempotent() throws Exception {
        String id = createAssessment("Final Exam", "EXAM");

        mockMvc.perform(get("/assessments/{id}", id)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.type").value("EXAM"));

        MvcResult firstStart = mockMvc.perform(post("/assessments/{id}/start", id)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value(id))
                .andReturn();
        String firstToken = objectMapper.readTree(firstStart.getResponse().getContentAsString())
                .get("token").asText();
        assertThat(firstToken).isNotBlank();

        MvcResult secondStart = mockMvc.perform(post("/assessments/{id}/start", id)
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andReturn();
        String secondToken = objectMapper.readTree(secondStart.getResponse().getContentAsString())
                .get("token").asText();

        assertThat(secondToken).isEqualTo(firstToken);
    }

    private String createAssessment(String title, String type) throws Exception {
        String body = type == null
                ? "{\"courseId\":\"course-1\",\"title\":\"" + title + "\"}"
                : "{\"courseId\":\"course-1\",\"title\":\"" + title + "\",\"type\":\"" + type + "\"}";

        MvcResult createResult = mockMvc.perform(post("/assessments")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        return created.get("id").asText();
    }
}
