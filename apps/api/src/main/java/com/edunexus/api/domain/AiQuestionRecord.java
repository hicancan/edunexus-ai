package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record AiQuestionRecord(
        UUID id,
        UUID sessionId,
        UUID studentId,
        int totalQuestions,
        int correctCount,
        int totalScore,
        Instant submittedAt) {}
