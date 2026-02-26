package com.edunexus.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AiClient {
    private final RestClient client;
    private final String baseUrl;
    private final String serviceToken;

    public AiClient(
            RestClient client,
            @Value("${app.ai-service-base-url}") String baseUrl,
            @Value("${app.ai-service-token}") String serviceToken
    ) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
    }

    public Map<String, Object> chat(Map<String, Object> body) {
        return post("/internal/v1/rag/chat", body, 2, 500);
    }

    public Map<String, Object> analyzeWrong(Map<String, Object> body) {
        return post("/internal/v1/exercise/analyze", body, 2, 800);
    }

    public Map<String, Object> generateQuestions(Map<String, Object> body) {
        return post("/internal/v1/aiq/generate", body, 2, 1000);
    }

    public Map<String, Object> generatePlan(Map<String, Object> body) {
        return post("/internal/v1/lesson-plans/generate", body, 2, 1200);
    }

    public Map<String, Object> ingestKb(Map<String, Object> body) {
        return post("/internal/v1/kb/ingest", body, 4, 1200);
    }

    public Map<String, Object> deleteKb(Map<String, Object> body) {
        return post("/internal/v1/kb/delete", body, 2, 500);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, int attempts, long baseDelayMs) {
        String traceId = String.valueOf(body.getOrDefault("trace_id", UUID.randomUUID().toString()));
        String idemKey = String.valueOf(body.getOrDefault("idempotency_key", "")).trim();
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                RestClient.RequestBodySpec req = client.post()
                        .uri(baseUrl + path)
                        .header("X-Service-Token", serviceToken)
                        .header("X-Trace-Id", traceId);
                if (!idemKey.isBlank()) {
                    req = req.header("Idempotency-Key", idemKey);
                }
                return req
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
            } catch (RuntimeException ex) {
                last = ex;
                if (i >= attempts - 1) {
                    break;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(baseDelayMs * (i + 1));
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw last == null ? new RuntimeException("调用 AI 服务失败") : last;
    }
}
