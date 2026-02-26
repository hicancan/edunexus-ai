package com.edunexus.api.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherAdminApiContractIT extends ApiContractIntegrationBase {

    @Test
    void teacherEndpoints_shouldMatchContract() throws Exception {
        String teacherToken = loginAndGetAccessToken("teacher01", "12345678");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lesson.pdf",
                "application/pdf",
                "demo content".getBytes()
        );

        String uploadBody = mockMvc.perform(multipart("/api/v1/teacher/knowledge/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("Idempotency-Key", "doc-upload-001"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.filename").value("lesson.pdf"))
                .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId = objectMapper.readTree(uploadBody).path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/teacher/knowledge/documents")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", notNullValue()))
                .andExpect(jsonPath("$.data[0].status", notNullValue()));

        mockMvc.perform(delete("/api/v1/teacher/knowledge/documents/{documentId}", documentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        String planBody = mockMvc.perform(post("/api/v1/teacher/plans/generate")
                        .header("Authorization", "Bearer " + teacherToken)
                        .header("Idempotency-Key", "plan-gen-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "topic", "牛顿第二定律",
                                "gradeLevel", "高一",
                                "durationMins", 45
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.contentMd", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String planId = objectMapper.readTree(planBody).path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/teacher/plans")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));

        mockMvc.perform(put("/api/v1/teacher/plans/{planId}", planId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("contentMd", "# 教学目标\n- 更新版本"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId));

        String shareBody = mockMvc.perform(post("/api/v1/teacher/plans/{planId}/share", planId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planId").value(planId))
                .andExpect(jsonPath("$.data.shareToken", notNullValue()))
                .andExpect(jsonPath("$.data.shareUrl", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String shareToken = objectMapper.readTree(shareBody).path("data").path("shareToken").asText();

        mockMvc.perform(get("/api/v1/teacher/plans/shared/{shareToken}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId))
                .andExpect(jsonPath("$.data.contentMd", notNullValue()));

        mockMvc.perform(get("/api/v1/teacher/plans/{planId}/export", planId)
                        .param("format", "md")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentType("text/markdown;charset=UTF-8"));

        mockMvc.perform(get("/api/v1/teacher/students/{studentId}/analytics", "00000000-0000-0000-0000-000000000003")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId", notNullValue()))
                .andExpect(jsonPath("$.data.topWeakPoints", notNullValue()));

        mockMvc.perform(post("/api/v1/teacher/suggestions")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "studentId", "00000000-0000-0000-0000-000000000003",
                                "knowledgePoint", "牛顿第二定律",
                                "suggestion", "建议复习受力分析"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.studentId").value("00000000-0000-0000-0000-000000000003"));

        mockMvc.perform(delete("/api/v1/teacher/plans/{planId}", planId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void adminEndpoints_shouldMatchContract() throws Exception {
        String adminToken = loginAndGetAccessToken("admin", "12345678");

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));

        String createBody = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", randomUsername("admin_create"),
                                "password", "12345678",
                                "role", "TEACHER",
                                "email", "new-teacher@example.com"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String userId = objectMapper.readTree(createBody).path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/admin/users/{userId}", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "DISABLED"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        String resourcesBody = mockMvc.perform(get("/api/v1/admin/resources")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode resources = objectMapper.readTree(resourcesBody).path("data").path("content");
        String resourceId = resources.get(0).path("resourceId").asText();
        byte[] downloaded = mockMvc.perform(get("/api/v1/admin/resources/{resourceId}/download", resourceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        org.junit.jupiter.api.Assertions.assertTrue(downloaded.length > 0);

        mockMvc.perform(get("/api/v1/admin/audits")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", notNullValue()));

        mockMvc.perform(get("/api/v1/admin/dashboard/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers", notNullValue()))
                .andExpect(jsonPath("$.data.totalChatSessions", notNullValue()))
                .andExpect(jsonPath("$.data.totalKnowledgeChunks", notNullValue()));
    }
}
