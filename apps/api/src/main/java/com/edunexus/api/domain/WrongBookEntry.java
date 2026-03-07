package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record WrongBookEntry(
        UUID id,
        UUID studentId,
        UUID questionId,
        int wrongCount,
        Instant lastWrongTime,
        String status,
        Instant updatedAt) {}
