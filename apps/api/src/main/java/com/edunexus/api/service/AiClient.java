package com.edunexus.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class AiClient {
    private final RestClient client;
    private final String baseUrl;

    public AiClient(RestClient client, @Value("${app.ai-service-base-url}") String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> chat(Map<String, Object> body) {
        return post("/chat", body);
    }

    public Map<String, Object> analyzeWrong(Map<String, Object> body) {
        return post("/wrong-book/analyze", body);
    }

    public Map<String, Object> generateQuestions(Map<String, Object> body) {
        return post("/questions/generate", body);
    }

    public Map<String, Object> generatePlan(Map<String, Object> body) {
        return post("/plans/generate", body);
    }

    public Map<String, Object> ingestKb(Map<String, Object> body) {
        return post("/kb/ingest", body);
    }

    public Map<String, Object> deleteKb(Map<String, Object> body) {
        return post("/kb/delete", body);
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        return client.post()
                .uri(baseUrl + path)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
