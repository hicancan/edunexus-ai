package com.edunexus.api.domain;

public record DashboardMetrics(
        long totalUsers,
        long totalStudents,
        long totalTeachers,
        long totalAdmins,
        long totalChatSessions,
        long totalChatMessages,
        long totalExerciseRecords,
        long totalQuestions,
        long totalDocuments,
        long totalKnowledgeChunks,
        long totalLessonPlans,
        long totalAiQuestionSessions) {}
