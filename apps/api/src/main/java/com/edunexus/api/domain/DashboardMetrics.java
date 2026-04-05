package com.edunexus.api.domain;

import java.util.List;
import java.util.Map;

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
        long totalAiQuestionSessions,
        List<Map<String, Object>> executionDistribution,
        List<Map<String, Object>> responseBenchmarks,
        List<Map<String, Object>> strategyComparison,
        Map<String, Object> governanceSummary,
        List<Map<String, Object>> interventionOutcomes,
        List<Map<String, Object>> flowLinkage,
        List<Map<String, Object>> experimentComparisons) {}
