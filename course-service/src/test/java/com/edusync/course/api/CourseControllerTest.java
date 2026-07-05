package com.edusync.course.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests (real Spring context, real H2 database, real
 * Flyway migration) covering the endpoints the audits found completely
 * untested: create/publish authorization and not-found paths (TEST-04/
 * TEST-05 in the code review, TEST-03 in the portfolio evaluation).
 *
 * @Transactional rolls back each test's DB writes so tests do not leak state
 * into one another despite sharing one Spring context / H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/courses/health")).andExpect(status().isOk());
    }

    @Test
    void createRequiresInstructorRole() throws Exception {
        mockMvc.perform(post("/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS201","title":"Data Structures"}
                                """))
                // no X-User-Roles header at all
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void studentCannotCreateCourse() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("X-User-Roles", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS202","title":"Algorithms"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorCanCreateAndPublishOwnCourse() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/courses")
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS301","title":"Operating Systems"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.instructorId").value("instructor-1"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String id = created.get("id").asText();

        mockMvc.perform(post("/courses/{id}/publish", id)
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/courses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void nonOwningInstructorCannotPublishSomeoneElsesCourse() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/courses")
                        .header("X-User-Id", "instructor-owner")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS302","title":"Compilers"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = readId(createResult);

        mockMvc.perform(post("/courses/{id}/publish", id)
                        .header("X-User-Id", "instructor-someone-else")
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanPublishAnyCourseEvenWithoutOwningIt() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/courses")
                        .header("X-User-Id", "instructor-owner-2")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS303","title":"Distributed Systems"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = readId(createResult);

        mockMvc.perform(post("/courses/{id}/publish", id)
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void createRejectsDuplicateCourseCodeWithConflict() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS401","title":"Databases"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/courses")
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS401","title":"Databases (duplicate)"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void createRejectsBlankTitleWithValidationError() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS501","title":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getReturnsNotFoundForUnknownCourse() throws Exception {
        mockMvc.perform(get("/courses/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void publishReturnsNotFoundForUnknownCourse() throws Exception {
        mockMvc.perform(post("/courses/{id}/publish", "does-not-exist")
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsPaginatedEnvelope() throws Exception {
        mockMvc.perform(post("/courses")
                        .header("X-User-Id", "instructor-1")
                        .header("X-User-Roles", "INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CS601","title":"Networks"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/courses").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalItems").isNumber());
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        return created.get("id").asText();
    }
}
