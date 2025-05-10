package com.edusync.submission.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
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
    void similarityShouldReturnMatchesForSameAssessment() throws Exception {
        String payloadA = """
                {"assessmentId":"a-1","answers":[{"questionId":"q1","response":"Dynamic programming uses overlapping subproblems"}]}
                """;
        String payloadB = """
                {"assessmentId":"a-1","answers":[{"questionId":"q1","response":"Dynamic programming solves overlapping subproblems efficiently"}]}
                """;

        MvcResult aResult = mockMvc.perform(post("/submissions")
                        .header("X-User-Id", "u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadA))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult bResult = mockMvc.perform(post("/submissions")
                        .header("X-User-Id", "u-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadB))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode aJson = objectMapper.readTree(aResult.getResponse().getContentAsString());
        JsonNode bJson = objectMapper.readTree(bResult.getResponse().getContentAsString());
        String aId = aJson.get("id").asText();
        String bId = bJson.get("id").asText();

        mockMvc.perform(get("/submissions/{id}/similarity", aId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value("a-1"))
                .andExpect(jsonPath("$.matches[0].submissionId").value(bId))
                .andExpect(jsonPath("$.maxSimilarity").isNumber());
    }
}
