package com.edunexus.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final RestClient client;
    private final String baseUrl;
    private final String serviceToken;

    public AiClient(
            RestClient client,
            @Value("${app.ai-service-base-url}") String baseUrl,
            @Value("${app.ai-service-token}") String serviceToken) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.serviceToken = serviceToken;
    }

    // doc/12-内部服务契约 §3.2: 同步调用最多 1 次重试 (2 attempts), 超时 25s
    public Map<String, Object> chat(Map<String, Object> body) {
        return post("/internal/v1/rag/chat", body, 2, 500);
    }

    // doc/12-内部服务契约 §3.2: 同步调用最多 1 次重试 (2 attempts), 超时 30s
    public Map<String, Object> analyzeWrong(Map<String, Object> body) {
        return post("/internal/v1/exercise/analyze", body, 2, 800);
    }

    // doc/12-内部服务契约 §3.2: 同步调用最多 1 次重试 (2 attempts), 超时 45s
    public Map<String, Object> generateQuestions(Map<String, Object> body) {
        return post("/internal/v1/aiq/generate", body, 2, 1000);
    }

    // doc/12-内部服务契约 §3.2: 同步调用最多 1 次重试 (2 attempts), 超时 60s
    public Map<String, Object> generatePlan(Map<String, Object> body) {
        return post("/internal/v1/lesson-plans/generate", body, 2, 1200);
    }

    // doc/12-内部服务契约 §3.2: 文档处理最多 3 次重试 (4 attempts), 超时 120s
    public Map<String, Object> ingestKb(Map<String, Object> body) {
        return post("/internal/v1/kb/ingest", body, 4, 1200);
    }

    // doc/12-内部服务契约 §3.2: 同步调用最多 1 次重试 (2 attempts), 超时 30s
    public Map<String, Object> deleteKb(Map<String, Object> body) {
        return post("/internal/v1/kb/delete", body, 2, 500);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, int maxAttempts, long baseDelayMs) {
        String traceId = Objects
                .toString(body.getOrDefault("traceId", body.getOrDefault("trace_id", UUID.randomUUID().toString())));
        String idemKey = Objects.toString(body.getOrDefault("idempotencyKey", body.getOrDefault("idempotency_key", "")))
                .trim();
        long startMs = System.currentTimeMillis();
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
                Map<String, Object> result = req
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                long elapsed = System.currentTimeMillis() - startMs;
                log.info("ai_call path={} attempt={} latency_ms={} trace_id={}", path, attempt, elapsed, traceId);
                return result;
            } catch (RestClientResponseException ex) {
                last = ex;
                log.warn("ai_call_error path={} attempt={} status={} trace_id={}", path, attempt,
                        ex.getStatusCode().value(), traceId);
                if (!isRetryableStatus(ex.getStatusCode().value()) || attempt >= maxAttempts) {
                    break;
                }
                sleep(baseDelayMs, attempt, true);
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("ai_call_error path={} attempt={} error={} trace_id={}", path, attempt, ex.getMessage(),
                        traceId);
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
