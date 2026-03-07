package com.edunexus.api.domain;

import java.util.UUID;

public record Classroom(UUID id, UUID teacherId, String name, String status, int studentCount) {}
