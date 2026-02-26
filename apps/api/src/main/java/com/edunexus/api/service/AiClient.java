package com.edunexus.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

    private Map<String, Object> post(String path, Map<String, Object> body, int maxAttempts, long baseDelayMs) {
        String traceId = Objects.toString(body.getOrDefault("traceId", body.getOrDefault("trace_id", UUID.randomUUID().toString())));
        String idemKey = Objects.toString(body.getOrDefault("idempotencyKey", body.getOrDefault("idempotency_key", ""))).trim();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RestClient.RequestBodySpec req = client.post()
                        .uri(baseUrl + path)
                        .header("X-Service-Token", serviceToken)
                        .header("X-Trace-Id", traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON);
                if (!idemKey.isBlank()) {
                    req = req.header("Idempotency-Key", idemKey);
                }
                return req
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
            } catch (RestClientResponseException ex) {
                last = ex;
                if (!isRetryableStatus(ex.getStatusCode().value()) || attempt >= maxAttempts) {
                    break;
                }
                sleep(baseDelayMs, attempt, true);
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt >= maxAttempts) {
                    break;
                }
                sleep(baseDelayMs, attempt, path.startsWith("/internal/v1/kb/"));
            }
        }
        throw last == null ? new RuntimeException("调用 AI 服务失败") : last;
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private void sleep(long baseDelayMs, int attempt, boolean withJitter) {
        long multiplier = 1L << Math.max(0, attempt - 1);
        long delay = baseDelayMs * multiplier;
        if (withJitter) {
            long jitter = ThreadLocalRandom.current().nextLong(50, 301);
            delay += jitter;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
