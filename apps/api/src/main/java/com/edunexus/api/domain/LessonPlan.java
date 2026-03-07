package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record LessonPlan(
        UUID id,
        UUID teacherId,
        String topic,
        String gradeLevel,
        int durationMins,
        String contentMd,
        boolean isShared,
        String shareToken,
        Instant sharedAt,
        Instant createdAt,
        Instant updatedAt) {}
