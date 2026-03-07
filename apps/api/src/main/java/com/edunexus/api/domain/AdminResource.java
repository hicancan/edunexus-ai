package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record AdminResource(
        UUID resourceId,
        String resourceType,
        String title,
        UUID creatorId,
        String creatorUsername,
        Instant createdAt) {}
