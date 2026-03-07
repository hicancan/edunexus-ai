package com.edunexus.api.domain;

import java.time.Instant;
import java.util.UUID;

public record Document(
        UUID id,
        UUID teacherId,
        UUID classroomId,
        String classroomName,
        String filename,
        String fileType,
        long fileSize,
        String storagePath,
        String status,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {}
