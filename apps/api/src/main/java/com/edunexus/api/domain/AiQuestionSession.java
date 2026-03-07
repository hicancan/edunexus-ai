package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record AiQuestionSession(
        UUID id,
        UUID studentId,
        String subject,
        String difficulty,
        int questionCount,
        boolean completed,
        Double correctRate,
        Integer score,
        UUID recordId,
        String contextSnapshotJson,
        Instant generatedAt,
        Instant updatedAt) {}
