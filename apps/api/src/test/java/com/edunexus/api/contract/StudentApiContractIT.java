package com.edunexus.api.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentApiContractIT extends ApiContractIntegrationBase {

    @Test
    void chatEndpoints_shouldMatchContract() throws Exception {
        String token = loginAndGetAccessToken("student01", "12345678");

        String createBody = mockMvc.perform(post("/api/v1/student/chat/session")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.studentId", notNullValue()))
                .andExpect(jsonPath("$.data.title").value("新建对话"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String sessionId = objectMapper.readTree(createBody).path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/student/chat/sessions")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/v1/student/chat/session/{sessionId}/message", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "请解释牛顿第二定律"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userMessage.id", notNullValue()))
                .andExpect(jsonPath("$.data.assistantMessage.id", notNullValue()))
                .andExpect(jsonPath("$.data.assistantMessage.citations", notNullValue()));

        mockMvc.perform(get("/api/v1/student/chat/session/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sessionId))
                .andExpect(jsonPath("$.data.messages", notNullValue()));

        mockMvc.perform(delete("/api/v1/student/chat/session/{sessionId}", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void exerciseEndpoints_shouldMatchContract() throws Exception {
        String token = loginAndGetAccessToken("student01", "12345678");

        String questionsBody = mockMvc.perform(get("/api/v1/student/exercise/questions")
                        .param("subject", "物理")
                        .param("difficulty", "MEDIUM")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andExpect(jsonPath("$.data.page").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode questionJson = objectMapper.readTree(questionsBody);
        String questionId = questionJson.path("data").path("content").get(0).path("id").asText();

        String submitBody = mockMvc.perform(post("/api/v1/student/exercise/submit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "answers", List.of(Map.of("questionId", questionId, "userAnswer", "A")),
                                "timeSpent", 30
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId", notNullValue()))
                .andExpect(jsonPath("$.data.totalQuestions").value(1))
                .andExpect(jsonPath("$.data.items", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String recordId = objectMapper.readTree(submitBody).path("data").path("recordId").asText();

        mockMvc.perform(get("/api/v1/student/exercise/{recordId}/analysis", recordId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value(recordId))
                .andExpect(jsonPath("$.data.items", notNullValue()));

        String wrongBody = mockMvc.perform(get("/api/v1/student/exercise/wrong-questions")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String wrongQuestionId = objectMapper.readTree(wrongBody).path("data").path("content").get(0).path("questionId").asText();
        mockMvc.perform(delete("/api/v1/student/exercise/wrong-questions/{questionId}", wrongQuestionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/student/exercise/records")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));
    }

    @Test
    void aiQuestionEndpoints_shouldMatchContract() throws Exception {
        String token = loginAndGetAccessToken("student01", "12345678");

        String generateBody = mockMvc.perform(post("/api/v1/student/ai-questions/generate")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "aiq-generate-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "count", 2,
                                "subject", "物理",
                                "difficulty", "MEDIUM",
                                "conceptTags", List.of("牛顿第二定律")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId", notNullValue()))
                .andExpect(jsonPath("$.data.questions", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode generateJson = objectMapper.readTree(generateBody);
        String sessionId = generateJson.path("data").path("sessionId").asText();
        JsonNode generatedQuestions = generateJson.path("data").path("questions");

        mockMvc.perform(get("/api/v1/student/ai-questions")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));

        List<Map<String, String>> answers = new ArrayList<>();
        for (JsonNode question : generatedQuestions) {
            answers.add(Map.of(
                    "questionId", question.path("id").asText(),
                    "userAnswer", question.path("correctAnswer").asText()
            ));
        }

        String submitBody = mockMvc.perform(post("/api/v1/student/ai-questions/submit")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "aiq-submit-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sessionId", sessionId,
                                "answers", answers
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId", notNullValue()))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String recordId = objectMapper.readTree(submitBody).path("data").path("recordId").asText();
        mockMvc.perform(get("/api/v1/student/ai-questions/{recordId}/analysis", recordId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value(recordId))
                .andExpect(jsonPath("$.data.items", notNullValue()));
    }
}
