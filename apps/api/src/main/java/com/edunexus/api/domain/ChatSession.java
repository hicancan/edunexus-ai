package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record ChatSession(
        UUID id,
        UUID studentId,
        String title,
        boolean isDeleted,
        Instant createdAt,
        Instant updatedAt) {}
