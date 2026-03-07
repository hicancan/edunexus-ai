package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        UUID sessionId,
        String role,
        String content,
        String citationsJson,
        int tokenUsage,
        Instant createdAt) {}
