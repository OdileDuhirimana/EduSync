package com.edusync.grading.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
    void regradeWorkflowShouldApproveOverride() throws Exception {
        String manualPayload = """
                {"breakdown":{"q1":30,"q2":40},"feedback":"Good effort"}
                """;
        mockMvc.perform(post("/grading/manual/sub-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score.total").value(70));

        String requestPayload = """
                {"requestedBy":"u-student","reason":"Please review rubric interpretation"}
                """;
        MvcResult requestResult = mockMvc.perform(post("/grading/regrade/sub-1/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        JsonNode requestJson = objectMapper.readTree(requestResult.getResponse().getContentAsString());
        String requestId = requestJson.get("requestId").asText();

        String decisionPayload = """
                {"moderatorId":"u-instructor","decision":"APPROVE","note":"Accepted","overrideTotal":82}
                """;
        mockMvc.perform(post("/grading/regrade/{requestId}/decision", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.currentGrade.total").value(82));
    }
}
