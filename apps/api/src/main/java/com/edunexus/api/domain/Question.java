package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record Question(
        UUID id,
        String subject,
        String questionType,
        String difficulty,
        String content,
        String optionsJson,
        String correctAnswer,
        String analysis,
        String knowledgePointsJson,
        int score,
        String source,
        UUID aiSessionId,
        UUID createdBy,
        boolean isActive,
        Instant createdAt) {}
