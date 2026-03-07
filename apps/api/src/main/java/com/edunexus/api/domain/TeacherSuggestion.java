package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record TeacherSuggestion(
        UUID id,
        UUID teacherId,
        UUID studentId,
        UUID questionId,
        String knowledgePoint,
        String suggestion,
        Instant createdAt) {}
