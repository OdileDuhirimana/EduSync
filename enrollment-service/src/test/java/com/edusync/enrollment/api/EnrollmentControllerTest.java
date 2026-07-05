package com.edusync.enrollment.api;

import com.edusync.enrollment.client.CourseClient;
import com.edusync.enrollment.client.CourseSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests (real Spring context, real H2 database, real
 * Flyway migration) covering the endpoints the audits found completely
 * untested: create/drop authorization and not-found paths (TEST-04/TEST-05
 * in the code review, TEST-03 in the portfolio evaluation).
 *
 * @Transactional rolls back each test's DB writes so tests do not leak state
 * into one another despite sharing one Spring context / H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnrollmentControllerTest {

    /**
     * WHY a fake {@link CourseClient} instead of a real HTTP call to a real
     * course-service instance: this test suite verifies enrollment-service's
     * own behavior in isolation, matching how course-service/auth-service's
     * own test suites don't stand up sibling services either. A true
     * cross-service integration test (e.g. Testcontainers running both
     * services) is a documented follow-up in the README, not implemented
     * here. Spring Boot auto-registers {@code @TestConfiguration} classes
     * nested in a {@code @SpringBootTest} test class, and {@code @Primary}
     * ensures this fake wins over {@code RestClientCourseClient} for
     * autowiring.
     */
    @TestConfiguration
    static class StubCourseClientConfig {
        @Bean
        @Primary
        CourseClient stubCourseClient() {
            return courseId -> switch (courseId) {
                case "does-not-exist-course" -> Optional.empty();
                case "unpublished-course" -> Optional.of(new CourseSummary(courseId, "DRAFT"));
                default -> Optional.of(new CourseSummary(courseId, "PUBLISHED"));
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/enrollments/health")).andExpect(status().isOk());
    }

    @Test
    void createEnrollmentSucceeds() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS101"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value("CS101"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }

    @Test
    void createDuplicateActiveEnrollmentReturnsConflict() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS201"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS201"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void myEnrollmentsWithoutUserIdHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/enrollments/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myEnrollmentsReturnsPaginatedEnvelope() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS301"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/enrollments/me")
                        .header("X-User-Id", "user-3")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalItems").isNumber());
    }

    @Test
    void dropByOwnerSucceeds() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS401"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = readId(createResult);

        mockMvc.perform(delete("/enrollments/{id}", id)
                        .header("X-User-Id", "user-4"))
                .andExpect(status().isNoContent());
    }

    @Test
    void dropByNonOwnerNonPrivilegedReturnsForbidden() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS501"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = readId(createResult);

        mockMvc.perform(delete("/enrollments/{id}", id)
                        .header("X-User-Id", "user-6"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void dropByAdminSucceedsEvenThoughNotOwner() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS601"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = readId(createResult);

        mockMvc.perform(delete("/enrollments/{id}", id)
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void studentCannotEnrollADifferentUser() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "student-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS701","userId":"someone-else"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void instructorCanEnrollADifferentUser() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"CS702","userId":"student-2"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("student-2"));
    }

    @Test
    void createReturnsNotFoundWhenCourseDoesNotExist() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"does-not-exist-course"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void createReturnsConflictWhenCourseIsNotPublished() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .header("X-User-Id", "user-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":"unpublished-course"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void dropReturnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(delete("/enrollments/{id}", "does-not-exist")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        return created.get("id").asText();
    }
}
