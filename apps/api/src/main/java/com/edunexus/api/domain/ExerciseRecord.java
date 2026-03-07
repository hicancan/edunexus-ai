package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record ExerciseRecord(
        UUID id,
        UUID studentId,
        String subject,
        int totalQuestions,
        int correctCount,
        int totalScore,
        int timeSpent,
        Instant createdAt) {}
