package com.edunexus.api.domain;

import java.util.UUID;

public record TeacherStudent(
        UUID studentId, String username, UUID classroomId, String classroomName) {}
