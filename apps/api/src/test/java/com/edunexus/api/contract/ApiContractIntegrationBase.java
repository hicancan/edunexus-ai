package com.edunexus.api.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edunexus.api.service.AiClient;
import com.edunexus.api.service.ObjectStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class ApiContractIntegrationBase {
    private static final DatabaseConfig DATABASE = resolveDatabaseConfig();
    private static final int TEST_GRPC_PORT = findAvailablePort();

    private static DatabaseConfig resolveDatabaseConfig() {
        DatabaseConfig explicitConfig =
                DatabaseConfig.fromEnvironment(
                        "CI_POSTGRES_HOST",
                        "CI_POSTGRES_PORT",
                        "CI_POSTGRES_DB",
                        "CI_POSTGRES_USER",
                        "CI_POSTGRES_PASSWORD");
        if (explicitConfig != null) {
            return explicitConfig;
        }

        DatabaseConfig envConfig =
                DatabaseConfig.fromEnvironment(
                        "POSTGRES_HOST",
                        "POSTGRES_PORT",
                        "POSTGRES_DB",
                        "POSTGRES_USER",
                        "POSTGRES_PASSWORD");
        if (envConfig != null) {
            return envConfig;
        }

        DatabaseConfig localTestDb = DatabaseConfig.prepareLocalDefault();
        if (localTestDb != null) {
            return localTestDb;
        }

        PostgreSQLContainer<?> container =
                new PostgreSQLContainer<>("postgres:17-alpine")
                        .withDatabaseName("edunexus_test")
                        .withUsername("edunexus")
                        .withPassword("edunexus");
        container.start();
        return DatabaseConfig.fromContainer(container);
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
        registry.add("JWT_SECRET", () -> "integration-test-secret-for-edunexus-very-strong-123456");
        registry.add("AI_SERVICE_TOKEN", () -> "test-service-token");
        registry.add("app.grpc.server.port", () -> TEST_GRPC_PORT);
        registry.add("app.document-dedupe-on-startup-enabled", () -> false);
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate a free gRPC test port", e);
        }
    }

    @Autowired protected MockMvc mockMvc;

    @Autowired protected ObjectMapper objectMapper;

    @MockitoBean protected ObjectStorageService objectStorageService;

    @MockitoBean protected AiClient aiClient;

    @BeforeEach
    void setupExternalStubs() {
        when(objectStorageService.upload(anyString(), anyString(), any(byte[].class)))
                .thenReturn("s3://test-bucket/doc.bin");
        when(objectStorageService.download(anyString())).thenReturn("demo-binary".getBytes());
        doNothing().when(objectStorageService).delete(anyString());

        when(aiClient.chat(any()))
                .thenReturn(
                        Map.of(
                                "answer",
                                "这是一个基于课堂资料的回答。",
                                "citations",
                                List.of(
                                        Map.of(
                                                "documentId",
                                                        "00000000-0000-0000-0000-000000000999",
                                                "filename", "physics.pdf",
                                                "chunkIndex", 1,
                                                "content", "F=ma",
                                                "score", 0.92)),
                                "provider",
                                "ollama",
                                "model",
                                "qwen3:8b",
                                "tokenUsage",
                                Map.of("prompt", 120, "completion", 80),
                                "latencyMs",
                                800));

        when(aiClient.generateQuestions(any()))
                .thenReturn(
                        Map.of(
                                "questions",
                                List.of(
                                        Map.of(
                                                "question_type", "SINGLE_CHOICE",
                                                "content", "牛顿第二定律的公式是？",
                                                "options",
                                                        Map.of(
                                                                "A", "F=ma", "B", "E=mc^2", "C",
                                                                "p=mv", "D", "v=s/t"),
                                                "correct_answer", "A",
                                                "explanation", "由力学基本定义得出。",
                                                "knowledge_points", List.of("牛顿第二定律")),
                                        Map.of(
                                                "question_type", "SINGLE_CHOICE",
                                                "content", "单位牛顿对应哪种量？",
                                                "options",
                                                        Map.of(
                                                                "A", "质量", "B", "力", "C", "速度", "D",
                                                                "功"),
                                                "correct_answer", "B",
                                                "explanation", "牛顿是力的单位。",
                                                "knowledge_points", List.of("力学单位"))),
                                "routerDecision",
                                "default"));

        when(aiClient.generatePlan(any()))
                .thenReturn(
                        Map.of(
                                "contentMd",
                                        "# 教学目标\n- 掌握牛顿第二定律\n\n# 重难点\n- 力与加速度关系\n\n# 教学流程\n1. 导入\n2. 讲解\n\n# 作业与评估\n- 课堂练习",
                                "provider", "ollama",
                                "model", "deepseek-r1:8b",
                                "latencyMs", 1200));

        when(aiClient.ingestKb(any()))
                .thenReturn(
                        Map.of(
                                "status",
                                "ok",
                                "jobId",
                                "job-doc-ingest-001",
                                "background",
                                true,
                                "chunks",
                                4));
        when(aiClient.deleteKb(any())).thenReturn(Map.of("status", "ok"));
    }

    protected String loginAndGetAccessToken(String username, String password) throws Exception {
        String payload =
                objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/login")
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

    protected String uniqueIdempotencyKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record DatabaseConfig(String url, String username, String password) {
        private static DatabaseConfig fromEnvironment(
                String hostEnv, String portEnv, String dbEnv, String userEnv, String passwordEnv) {
            String host = System.getenv(hostEnv);
            if (host == null || host.isBlank()) {
                return null;
            }
            return new DatabaseConfig(
                    String.format(
                            "jdbc:postgresql://%s:%s/%s",
                            host,
                            envOrDefault(portEnv, "5432"),
                            envOrDefault(dbEnv, "edunexus_test")),
                    envOrDefault(userEnv, "edunexus"),
                    envOrDefault(passwordEnv, "edunexus"));
        }

        private static DatabaseConfig fromContainer(PostgreSQLContainer<?> container) {
            return new DatabaseConfig(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword());
        }

        private static DatabaseConfig prepareLocalDefault() {
            DatabaseConfig adminConfig =
                    new DatabaseConfig(
                            "jdbc:postgresql://127.0.0.1:5432/postgres", "postgres", "postgres");
            if (!adminConfig.isReachable()) {
                return null;
            }

            DatabaseConfig testConfig =
                    new DatabaseConfig(
                            "jdbc:postgresql://127.0.0.1:5432/edunexus_test",
                            "postgres",
                            "postgres");
            try {
                ensureDatabaseExists(adminConfig, "edunexus_test");
                resetSchema(testConfig);
                return testConfig;
            } catch (SQLException ignored) {
                return null;
            }
        }

        private boolean isReachable() {
            try {
                DriverManager.setLoginTimeout(2);
                try (Connection ignored = DriverManager.getConnection(url, username, password)) {
                    return true;
                }
            } catch (SQLException ignored) {
                return false;
            }
        }

        private static void ensureDatabaseExists(DatabaseConfig adminConfig, String databaseName)
                throws SQLException {
            try (Connection connection =
                            DriverManager.getConnection(
                                    adminConfig.url, adminConfig.username, adminConfig.password);
                    PreparedStatement exists =
                            connection.prepareStatement(
                                    "select 1 from pg_database where datname = ?")) {
                exists.setString(1, databaseName);
                boolean present;
                try (var resultSet = exists.executeQuery()) {
                    present = resultSet.next();
                }
                if (present) {
                    return;
                }
                try (Statement create = connection.createStatement()) {
                    create.execute("create database " + databaseName);
                }
            }
        }

        private static void resetSchema(DatabaseConfig config) throws SQLException {
            try (Connection connection =
                            DriverManager.getConnection(
                                    config.url, config.username, config.password);
                    Statement statement = connection.createStatement()) {
                statement.execute("drop schema if exists public cascade");
                statement.execute("create schema public");
            }
        }

        private static String envOrDefault(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }
}
