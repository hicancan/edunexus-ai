package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditLog(
        UUID id,
        UUID actorId,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        String detailJson,
        String ip,
        Instant createdAt) {}
