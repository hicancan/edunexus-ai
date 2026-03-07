package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record ExerciseRecordItem(
        UUID id,
        UUID recordId,
        UUID questionId,
        String userAnswer,
        String correctAnswer,
        boolean isCorrect,
        int score,
        String analysis,
        String teacherSuggestion,
        Instant createdAt) {}
