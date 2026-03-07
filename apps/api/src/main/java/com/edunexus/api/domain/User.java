package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String username,
        String role,
        String status,
        String email,
        String phone,
        Instant createdAt,
        Instant updatedAt) {}
