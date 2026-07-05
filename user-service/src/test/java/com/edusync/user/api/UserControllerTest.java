package com.edusync.user.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests (real Spring context, real H2 database, real
 * Flyway migration).
 *
 * @Transactional rolls back each test's DB writes so tests do not leak state
 * into one another despite sharing one Spring context / H2 instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldReturnOk() throws Exception {
        mockMvc.perform(get("/users/health")).andExpect(status().isOk());
    }

    @Test
    void getMeRequiresUserIdHeader() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void patchMeRequiresUserIdHeader() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Jane","lastName":"Doe"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void patchMeRejectsBlankFirstNameWithValidationError() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .header("X-User-Id", "user-blank-first")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"","lastName":"Doe"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void patchMeRejectsBlankLastNameWithValidationError() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .header("X-User-Id", "user-blank-last")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Jane","lastName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getMeCreatesDefaultProfileOnFirstCallForABrandNewCaller() throws Exception {
        mockMvc.perform(get("/users/me")
                        .header("X-User-Id", "user-brand-new")
                        .header("X-User-Email", "brandnew@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-brand-new"))
                .andExpect(jsonPath("$.email").value("brandnew@example.com"))
                .andExpect(jsonPath("$.firstName").value("New"))
                .andExpect(jsonPath("$.lastName").value("User"));
    }

    /**
     * This is the assertion that actually proves the audited bug is fixed:
     * the old controller would pass a shallow "PATCH returns 200" test while
     * never persisting anything. Only a subsequent, independent GET can
     * prove the write really landed.
     */
    @Test
    void patchThenGetReflectsTheExactValuesJustPatched() throws Exception {
        String userId = "user-roundtrip-1";

        mockMvc.perform(patch("/users/me")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ada","lastName":"Lovelace"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"));

        mockMvc.perform(get("/users/me").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"));
    }
}
