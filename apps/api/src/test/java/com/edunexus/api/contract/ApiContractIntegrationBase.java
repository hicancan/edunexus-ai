package com.edunexus.api.contract;

import com.edunexus.api.service.ObjectStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class ApiContractIntegrationBase {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static final MockWebServer AI_SERVER;

    static {
        try {
            AI_SERVER = new MockWebServer();
            AI_SERVER.start();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("JWT_SECRET", () -> "integration-test-secret-for-edunexus-very-strong-123456");
        registry.add("AI_SERVICE_TOKEN", () -> "test-service-token");
        registry.add("app.ai-service-base-url", () -> AI_SERVER.url("/").toString().replaceAll("/$", ""));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected ObjectStorageService objectStorageService;

    @BeforeEach
    void setupExternalStubs() {
        when(objectStorageService.upload(anyString(), anyString(), any(byte[].class))).thenReturn("s3://test-bucket/doc.bin");
        when(objectStorageService.download(anyString())).thenReturn("demo-binary".getBytes());
        doNothing().when(objectStorageService).delete(anyString());

        AI_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (!"test-service-token".equals(request.getHeader("X-Service-Token"))) {
                    return json(401, Map.of("code", "INTERNAL_AUTH_FAILED", "message", "服务间认证失败"));
                }

                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/internal/v1/rag/chat")) {
                    return json(200, Map.of(
                            "answer", "这是一个基于课堂资料的回答。",
                            "citations", List.of(Map.of(
                                    "documentId", "00000000-0000-0000-0000-000000000999",
                                    "filename", "physics.pdf",
                                    "chunkIndex", 1,
                                    "content", "F=ma",
                                    "score", 0.92
                            )),
                            "provider", "ollama",
                            "model", "qwen3:8b",
                            "tokenUsage", Map.of("prompt", 120, "completion", 80),
                            "latencyMs", 800
                    ));
                }
                if (path.startsWith("/internal/v1/aiq/generate")) {
                    return json(200, Map.of(
                            "questions", List.of(
                                    Map.of(
                                            "question_type", "SINGLE_CHOICE",
                                            "content", "牛顿第二定律的公式是？",
                                            "options", Map.of("A", "F=ma", "B", "E=mc^2", "C", "p=mv", "D", "v=s/t"),
                                            "correct_answer", "A",
                                            "explanation", "由力学基本定义得出。",
                                            "knowledge_points", List.of("牛顿第二定律")
                                    ),
                                    Map.of(
                                            "question_type", "SINGLE_CHOICE",
                                            "content", "单位牛顿对应哪种量？",
                                            "options", Map.of("A", "质量", "B", "力", "C", "速度", "D", "功"),
                                            "correct_answer", "B",
                                            "explanation", "牛顿是力的单位。",
                                            "knowledge_points", List.of("力学单位")
                                    )
                            )
                    ));
                }
                if (path.startsWith("/internal/v1/lesson-plans/generate")) {
                    return json(200, Map.of(
                            "contentMd", "# 教学目标\n- 掌握牛顿第二定律\n\n# 重难点\n- 力与加速度关系\n\n# 教学流程\n1. 导入\n2. 讲解\n\n# 作业与评估\n- 课堂练习",
                            "provider", "ollama",
                            "model", "deepseek-r1:8b",
                            "latencyMs", 1200
                    ));
                }
                if (path.startsWith("/internal/v1/kb/ingest")) {
                    return json(200, Map.of("chunks", 4));
                }
                if (path.startsWith("/internal/v1/kb/delete")) {
                    return json(200, Map.of("deleted", true));
                }
                return json(404, Map.of("code", "NOT_FOUND", "message", "mock path not found"));
            }
        });
    }

    protected String loginAndGetAccessToken(String username, String password) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.path("data").path("accessToken").asText();
    }

    protected String randomUsername(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private MockResponse json(int status, Object body) {
        try {
            return new MockResponse()
                    .setResponseCode(status)
                    .setHeader("Content-Type", "application/json")
                    .setBody(objectMapper.writeValueAsString(body));
        } catch (Exception ex) {
            return new MockResponse().setResponseCode(500).setBody("{}");
        }
    }
}
