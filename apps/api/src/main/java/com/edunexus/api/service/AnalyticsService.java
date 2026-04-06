package com.edunexus.api.service;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.domain.TeacherStudent;
import com.edunexus.api.repository.SuggestionRepository;
import com.edunexus.api.repository.TeacherStudentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final AiClient aiClient;
    private final RealtimeStudentStateService realtimeStateService;
    private final SuggestionRepository suggestionRepo;
    private final TeacherStudentRepository teacherStudentRepo;
    private final JdbcTemplate jdbc;

    public AnalyticsService(
            AiClient aiClient,
            RealtimeStudentStateService realtimeStateService,
            SuggestionRepository suggestionRepo,
            TeacherStudentRepository teacherStudentRepo,
            JdbcTemplate jdbc) {
        this.aiClient = aiClient;
        this.realtimeStateService = realtimeStateService;
        this.suggestionRepo = suggestionRepo;
        this.teacherStudentRepo = teacherStudentRepo;
        this.jdbc = jdbc;
    }

    public List<TeacherStudent> listStudents(UUID teacherId) {
        return teacherStudentRepo.listByTeacher(teacherId);
    }

    public void ensureStudentLinked(UUID teacherId, UUID studentId) {
        teacherStudentRepo.ensureLinked(teacherId, studentId);
    }

    public Map<String, Object> getStudentAnalytics(UUID studentId) {
        String username = getStudentUsername(studentId);
        long totalExercises =
                countOf(
                        """
                        select count(*)
                        from (
                          select id
                          from exercise_records
                          where student_id = ?
                          union all
                          select id
                          from ai_question_records
                          where student_id = ?
                        ) practice_records
                        """,
                        studentId,
                        studentId);
        long totalQuestions =
                countOf(
                        """
                        select coalesce(sum(total_questions), 0)
                        from (
                          select total_questions
                          from exercise_records
                          where student_id = ?
                          union all
                          select total_questions
                          from ai_question_records
                          where student_id = ?
                        ) practice_records
                        """,
                        studentId,
                        studentId);
        long correctCount =
                countOf(
                        """
                        select coalesce(sum(correct_count), 0)
                        from (
                          select correct_count
                          from exercise_records
                          where student_id = ?
                          union all
                          select correct_count
                          from ai_question_records
                          where student_id = ?
                        ) practice_records
                        """,
                        studentId,
                        studentId);
        double averageScore =
                ApiDataMapper.asDouble(
                        jdbc
                                .queryForList(
                                        """
                                        select coalesce(avg(total_score), 0) as avg_score
                                        from (
                                          select total_score
                                          from exercise_records
                                          where student_id = ?
                                          union all
                                          select total_score
                                          from ai_question_records
                                          where student_id = ?
                                        ) practice_records
                                        """,
                                        studentId,
                                        studentId)
                                .stream()
                                .findFirst()
                                .map(r -> r.get("avg_score"))
                                .orElse(0));
        long wrongBookCount =
                countOf(
                        "select count(*) from wrong_book where student_id=? and status='ACTIVE'",
                        studentId);
        long masteredWrongCount =
                countOf(
                        "select count(*) from wrong_book where student_id=? and status='MASTERED'",
                        studentId);
        long totalAiQuestionSessions =
                countOf("select count(*) from ai_question_sessions where student_id=?", studentId);
        long completedAiQuestionSessions =
                countOf(
                        "select count(*) from ai_question_sessions where student_id=? and completed=true",
                        studentId);
        long teacherSuggestionCount =
                countOf("select count(*) from teacher_suggestions where student_id=?", studentId);
        long recentChatMessageCount =
                countOf(
                        """
                        select count(*)
                        from chat_messages m
                        join chat_sessions s on s.id = m.session_id
                        where s.student_id = ?
                          and m.role = 'USER'
                          and m.created_at >= now() - interval '7 day'
                        """,
                        studentId);
        long recentExerciseCount =
                countOf(
                        """
                        select count(*)
                        from exercise_records
                        where student_id = ?
                          and created_at >= now() - interval '7 day'
                        """,
                        studentId);
        long recentAiQuestionCount =
                countOf(
                        """
                        select count(*)
                        from ai_question_sessions
                        where student_id = ?
                          and generated_at >= now() - interval '7 day'
                        """,
                        studentId);
        long activeDays7d =
                countOf(
                        """
                        select count(distinct activity_date)
                        from (
                          select created_at::date as activity_date
                          from exercise_records
                          where student_id = ?
                            and created_at >= now() - interval '7 day'
                          union all
                          select generated_at::date as activity_date
                          from ai_question_sessions
                          where student_id = ?
                            and generated_at >= now() - interval '7 day'
                          union all
                          select m.created_at::date as activity_date
                          from chat_messages m
                          join chat_sessions s on s.id = m.session_id
                          where s.student_id = ?
                            and m.role = 'USER'
                            and m.created_at >= now() - interval '7 day'
                        ) activity_stream
                        """,
                        studentId,
                        studentId,
                        studentId);

        List<Map<String, Object>> topWeakPoints = buildTopWeakPoints(studentId, wrongBookCount);
        List<Map<String, Object>> recentPerformance = buildRecentPerformance(studentId);
        double recentAccuracy =
                recentPerformance.isEmpty()
                        ? 0D
                        : ApiDataMapper.asDouble(recentPerformance.getLast().get("accuracyRate"));
        double rollingAccuracy = rollingAccuracy(recentPerformance, totalQuestions, correctCount);
        double aiCompletionRate = percentage(completedAiQuestionSessions, totalAiQuestionSessions);
        RealtimeStudentStateService.RealtimeSnapshot realtimeSnapshot =
                realtimeStateService.snapshot(studentId);
        String interactionProfile =
                interactionProfile(
                        recentChatMessageCount,
                        recentExerciseCount,
                        recentAiQuestionCount,
                        realtimeSnapshot);
        Map<String, Object> supportStage =
                buildSupportStage(
                        recentAccuracy,
                        rollingAccuracy,
                        wrongBookCount,
                        activeDays7d,
                        realtimeSnapshot);
        List<String> behaviorSignals =
                buildBehaviorSignals(
                        recentChatMessageCount,
                        recentExerciseCount,
                        recentAiQuestionCount,
                        activeDays7d,
                        aiCompletionRate,
                        teacherSuggestionCount,
                        topWeakPoints,
                        realtimeSnapshot);
        List<String> recommendedActions =
                buildRecommendedActions(
                        topWeakPoints,
                        supportStage,
                        teacherSuggestionCount,
                        aiCompletionRate,
                        activeDays7d,
                        realtimeSnapshot);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentId", studentId.toString());
        data.put("username", username);
        data.put("totalExercises", totalExercises);
        data.put("totalQuestions", totalQuestions);
        data.put("correctCount", correctCount);
        data.put("averageScore", averageScore);
        data.put("wrongBookCount", wrongBookCount);
        data.put("masteredWrongCount", masteredWrongCount);
        data.put("recentAccuracy", recentAccuracy);
        data.put("rollingAccuracy", rollingAccuracy);
        data.put("aiCompletionRate", aiCompletionRate);
        data.put("recentChatMessageCount", recentChatMessageCount);
        data.put("recentAiQuestionCount", recentAiQuestionCount);
        data.put("recentExerciseCount", recentExerciseCount);
        data.put("activeDays7d", activeDays7d);
        data.put("latestActivityAt", latestActivityAt(studentId));
        data.put("teacherSuggestionCount", teacherSuggestionCount);
        data.put("interactionProfile", interactionProfile);
        data.put("behaviorSignals", behaviorSignals);
        data.put("recommendedActions", recommendedActions);
        data.put("supportStage", supportStage);
        data.put("recentPerformance", recentPerformance);
        data.put("topWeakPoints", topWeakPoints);
        data.put("realtimeState", toRealtimeStateVo(realtimeSnapshot));
        return data;
    }

    public Map<String, Object> getStudentAttribution(UUID studentId) {
        List<Map<String, Object>> topRows =
                jdbc.queryForList(
                        """
                select kp.knowledge_point,coalesce(sum(w.wrong_count),0) as wrong_count
                from wrong_book w
                join questions q on q.id=w.question_id
                join lateral jsonb_array_elements_text(coalesce(q.knowledge_points,'[]'::jsonb)) as kp(knowledge_point) on true
                where w.student_id=? and w.status='ACTIVE'
                group by kp.knowledge_point
                order by wrong_count desc, kp.knowledge_point asc
                limit 1
                """,
                        studentId);

        if (topRows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("knowledgePoint", null);
            empty.put("impactCount", 0);
            empty.put("examples", List.of());
            empty.put("summary", null);
            return empty;
        }

        String knowledgePoint = String.valueOf(topRows.getFirst().get("knowledge_point"));
        int impactCount = ApiDataMapper.asInt(topRows.getFirst().get("wrong_count"));

        List<Map<String, Object>> examples =
                jdbc
                        .queryForList(
                                """
                select left(q.content,160) as content,w.wrong_count,w.last_wrong_time
                from wrong_book w join questions q on q.id=w.question_id
                where w.student_id=? and w.status='ACTIVE'
                  and q.knowledge_points is not null
                  and jsonb_exists(q.knowledge_points,?)
                order by w.wrong_count desc, w.last_wrong_time desc
                limit 3
                """,
                                studentId,
                                knowledgePoint)
                        .stream()
                        .map(
                                row -> {
                                    Map<String, Object> out = new LinkedHashMap<>();
                                    out.put("content", ApiDataMapper.asString(row.get("content")));
                                    out.put(
                                            "wrongCount",
                                            ApiDataMapper.asInt(row.get("wrong_count")));
                                    out.put(
                                            "lastWrongTime",
                                            ApiDataMapper.asIsoTime(row.get("last_wrong_time")));
                                    return out;
                                })
                        .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("knowledgePoint", knowledgePoint);
        data.put("impactCount", impactCount);
        data.put("examples", examples);
        data.put("summary", "该学生在「" + knowledgePoint + "」上存在稳定错误模式，建议先复盘概念定义，再进行分层训练。");
        return data;
    }

    public List<Map<String, Object>> getInterventionRecommendations(UUID teacherId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                select kp.knowledge_point,count(distinct b.student_id) as student_count,
                       coalesce(sum(w.wrong_count),0) as total_wrong_count
                from teacher_student_bindings b
                join wrong_book w on w.student_id=b.student_id and w.status='ACTIVE'
                join questions q on q.id=w.question_id
                join lateral jsonb_array_elements_text(coalesce(q.knowledge_points,'[]'::jsonb)) as kp(knowledge_point) on true
                where b.teacher_id=? and b.status='ACTIVE' and (b.revoked_at is null or b.revoked_at > now())
                group by kp.knowledge_point
                order by total_wrong_count desc, student_count desc, kp.knowledge_point asc
                limit 8
                """,
                        teacherId);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, SuggestionRepository.DispatchSummary> dispatchSummaries =
                suggestionRepo.listDispatchSummaries(teacherId).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        SuggestionRepository.DispatchSummary::knowledgePoint,
                                        item -> item,
                                        (left, right) ->
                                                left.lastDispatchedAt() != null
                                                                && right.lastDispatchedAt() != null
                                                                && right.lastDispatchedAt()
                                                                        .isAfter(
                                                                                left
                                                                                        .lastDispatchedAt())
                                                        ? right
                                                        : left,
                                        java.util.LinkedHashMap::new));

        List<Map<String, Object>> candidates =
                rows.stream()
                        .map(
                                row ->
                                        Map.<String, Object>of(
                                                "knowledgePoint",
                                                String.valueOf(row.get("knowledge_point")),
                                                "studentCount",
                                                ApiDataMapper.asInt(row.get("student_count")),
                                                "totalWrongCount",
                                                ApiDataMapper.asInt(row.get("total_wrong_count"))))
                        .toList();

        Map<String, Object> aiResult =
                aiClient.generateTeacherSuggestions(
                        Map.of("teacherId", teacherId.toString(), "candidates", candidates));
        Map<String, String> generatedSuggestions =
                ApiDataMapper.parseObjectList(
                                aiResult.get("suggestions"),
                                new com.fasterxml.jackson.databind.ObjectMapper())
                        .stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        item -> ApiDataMapper.asString(item.get("knowledgePoint")),
                                        item ->
                                                ApiDataMapper.asString(
                                                        item.get("suggestionTemplate")),
                                        (left, right) -> left,
                                        java.util.LinkedHashMap::new));

        String provider = ApiDataMapper.asString(aiResult.get("provider"));
        String model = ApiDataMapper.asString(aiResult.get("model"));
        int latencyMs = ApiDataMapper.asInt(aiResult.get("latencyMs"));
        String routerDecision = ApiDataMapper.asString(aiResult.get("routerDecision"));

        return candidates.stream()
                .filter(item -> generatedSuggestions.containsKey(item.get("knowledgePoint")))
                .map(
                        item -> {
                            Map<String, Object> out = new LinkedHashMap<>(item);
                            String knowledgePoint =
                                    ApiDataMapper.asString(item.get("knowledgePoint"));
                            int studentCount = ApiDataMapper.asInt(item.get("studentCount"));
                            SuggestionRepository.DispatchSummary dispatchSummary =
                                    dispatchSummaries.get(knowledgePoint);
                            int dispatchedStudentCount =
                                    dispatchSummary == null
                                            ? 0
                                            : dispatchSummary.dispatchedStudentCount();
                            out.put("suggestionTemplate", generatedSuggestions.get(knowledgePoint));
                            out.put("generationSource", "AI");
                            out.put("provider", provider);
                            out.put("model", model);
                            out.put("latencyMs", latencyMs);
                            out.put("routerDecision", routerDecision);
                            out.put("dispatchedStudentCount", dispatchedStudentCount);
                            out.put(
                                    "dispatchedCoverageRate",
                                    percentage(dispatchedStudentCount, studentCount));
                            out.put(
                                    "fullyDispatched",
                                    studentCount > 0 && dispatchedStudentCount >= studentCount);
                            out.put(
                                    "lastDispatchedAt",
                                    dispatchSummary == null
                                            ? null
                                            : ApiDataMapper.asIsoTime(
                                                    dispatchSummary.lastDispatchedAt()));
                            return out;
                        })
                .toList();
    }

    private String getStudentUsername(UUID studentId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "select username from users where id=? and role='STUDENT' and deleted_at is null",
                        studentId);
        return rows.isEmpty() ? "" : String.valueOf(rows.getFirst().get("username"));
    }

    private List<Map<String, Object>> buildTopWeakPoints(UUID studentId, long wrongBookCount) {
        List<Map<String, Object>> weakRows =
                jdbc.queryForList(
                        """
                        select kp.knowledge_point, coalesce(sum(w.wrong_count), 0) as wrong_count
                        from wrong_book w
                        join questions q on q.id = w.question_id
                        join lateral jsonb_array_elements_text(coalesce(q.knowledge_points,'[]'::jsonb)) as kp(knowledge_point) on true
                        where w.student_id = ? and w.status = 'ACTIVE'
                        group by kp.knowledge_point
                        order by wrong_count desc, kp.knowledge_point asc
                        limit 5
                        """,
                        studentId);

        return weakRows.stream()
                .map(
                        row -> {
                            int wrongCount = ApiDataMapper.asInt(row.get("wrong_count"));
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("knowledgePoint", String.valueOf(row.get("knowledge_point")));
                            item.put("wrongCount", wrongCount);
                            item.put("errorRate", percentage(wrongCount, wrongBookCount));
                            return item;
                        })
                .toList();
    }

    private List<Map<String, Object>> buildRecentPerformance(UUID studentId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        select record_id, total_questions, correct_count, total_score, created_at, source
                        from (
                          select
                            id as record_id,
                            total_questions,
                            correct_count,
                            total_score,
                            created_at,
                            'EXERCISE' as source
                          from exercise_records
                          where student_id = ?
                          union all
                          select
                            id as record_id,
                            total_questions,
                            correct_count,
                            total_score,
                            submitted_at as created_at,
                            'AI_PRACTICE' as source
                          from ai_question_records
                          where student_id = ?
                        ) recent_records
                        order by created_at desc
                        limit 6
                        """,
                        studentId,
                        studentId);

        List<Map<String, Object>> reversed = new ArrayList<>(rows);
        java.util.Collections.reverse(reversed);

        return reversed.stream()
                .map(
                        row -> {
                            int total = ApiDataMapper.asInt(row.get("total_questions"));
                            int correct = ApiDataMapper.asInt(row.get("correct_count"));
                            Map<String, Object> point = new LinkedHashMap<>();
                            point.put("recordId", String.valueOf(row.get("record_id")));
                            point.put("totalQuestions", total);
                            point.put("correctCount", correct);
                            point.put("totalScore", ApiDataMapper.asInt(row.get("total_score")));
                            point.put("accuracyRate", percentage(correct, total));
                            point.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
                            point.put("source", ApiDataMapper.asString(row.get("source")));
                            return point;
                        })
                .toList();
    }

    private double rollingAccuracy(
            List<Map<String, Object>> recentPerformance, long totalQuestions, long correctCount) {
        if (!recentPerformance.isEmpty()) {
            double questions =
                    recentPerformance.stream()
                            .mapToDouble(item -> ApiDataMapper.asDouble(item.get("totalQuestions")))
                            .sum();
            double correct =
                    recentPerformance.stream()
                            .mapToDouble(item -> ApiDataMapper.asDouble(item.get("correctCount")))
                            .sum();
            return percentage(correct, questions);
        }
        return percentage(correctCount, totalQuestions);
    }

    private Map<String, Object> buildSupportStage(
            double recentAccuracy,
            double rollingAccuracy,
            long wrongBookCount,
            long activeDays7d,
            RealtimeStudentStateService.RealtimeSnapshot realtimeSnapshot) {
        Map<String, Object> stage = new LinkedHashMap<>();
        boolean realtimeEscalation =
                "LIVE".equals(realtimeSnapshot.dataState())
                        && ((realtimeSnapshot.recentErrorDensity() != null
                                        && realtimeSnapshot.recentErrorDensity() >= 50D)
                                || (realtimeSnapshot.recentWrongCount() != null
                                        && realtimeSnapshot.recentWrongCount() >= 3));
        if (recentAccuracy < 60D || wrongBookCount >= 5 || realtimeEscalation) {
            stage.put("label", "重点支架期");
            stage.put(
                    "description",
                    realtimeEscalation
                            ? "近 10 分钟课堂态出现明显波动，当前更需要教师点拨、低门槛再练和错因复盘的连续支持。"
                            : "当前更需要教师点拨、低门槛再练和错因复盘的连续支持。");
            stage.put("tone", "danger");
            stage.put("supportZone", "高支架支持区");
            return stage;
        }
        if (rollingAccuracy < 80D || wrongBookCount >= 2 || activeDays7d <= 2) {
            stage.put("label", "巩固提升期");
            stage.put("description", "基础概念已开始回稳，但仍需围绕薄弱点做针对性迁移练习。");
            stage.put("tone", "warning");
            stage.put("supportZone", "概念巩固区");
            return stage;
        }
        stage.put("label", "迁移拓展期");
        stage.put("description", "当前掌握较稳定，可转向综合应用、迁移题和课堂深度提问。");
        stage.put("tone", "success");
        stage.put("supportZone", "迁移拓展区");
        return stage;
    }

    private List<String> buildBehaviorSignals(
            long recentChatMessageCount,
            long recentExerciseCount,
            long recentAiQuestionCount,
            long activeDays7d,
            double aiCompletionRate,
            long teacherSuggestionCount,
            List<Map<String, Object>> topWeakPoints,
            RealtimeStudentStateService.RealtimeSnapshot realtimeSnapshot) {
        List<String> signals = new ArrayList<>();
        if (realtimeSnapshot.signals() != null && !realtimeSnapshot.signals().isEmpty()) {
            signals.addAll(realtimeSnapshot.signals());
        }
        if (recentChatMessageCount >= 3) {
            signals.add("近 7 天课堂提问较活跃，说明能够在卡点处主动求助。");
        } else if (recentChatMessageCount == 0) {
            signals.add("近 7 天课堂提问较少，若仍有错题积压，建议教师主动触发点拨。");
        }

        if (recentExerciseCount >= 3) {
            signals.add("近期练习频次较高，具备持续修正和再练的投入度。");
        } else if (recentExerciseCount == 0) {
            signals.add("近期缺少再练记录，画像更新将偏慢，建议尽快完成一轮巩固练习。");
        }

        if (recentAiQuestionCount > 0) {
            signals.add("AI 个性化再练已被触发，当前完成率为 " + round2(aiCompletionRate) + "%。");
        }

        if (!topWeakPoints.isEmpty()) {
            signals.add(
                    "错误最集中于「"
                            + topWeakPoints.getFirst().get("knowledgePoint")
                            + "」，是当前最值得优先补齐的知识环节。");
        }

        if (teacherSuggestionCount > 0) {
            signals.add("教师侧已形成 " + teacherSuggestionCount + " 条干预建议，可直接纳入后续复盘。");
        }

        signals.add("近 7 天共有 " + activeDays7d + " 个活跃学习日，可用于判断连续学习稳定性。");
        return signals;
    }

    private List<String> buildRecommendedActions(
            List<Map<String, Object>> topWeakPoints,
            Map<String, Object> supportStage,
            long teacherSuggestionCount,
            double aiCompletionRate,
            long activeDays7d,
            RealtimeStudentStateService.RealtimeSnapshot realtimeSnapshot) {
        List<String> actions = new ArrayList<>();
        if ("LIVE".equals(realtimeSnapshot.dataState())
                && realtimeSnapshot.hotspotKnowledgePoints() != null
                && !realtimeSnapshot.hotspotKnowledgePoints().isEmpty()) {
            actions.add(
                    "先处理近 10 分钟课堂态热点「"
                            + realtimeSnapshot
                                    .hotspotKnowledgePoints()
                                    .getFirst()
                                    .get("knowledgePoint")
                            + "」，先用 1 道低门槛诊断题确认是否补齐。");
        }
        if (!topWeakPoints.isEmpty()) {
            actions.add(
                    "围绕「"
                            + topWeakPoints.getFirst().get("knowledgePoint")
                            + "」先复盘概念定义，再完成一轮低门槛再练。");
        }
        if (teacherSuggestionCount > 0) {
            actions.add("优先执行教师已下发的干预建议，并在下一轮练习后对比画像变化。");
        }
        if (aiCompletionRate > 0D && aiCompletionRate < 60D) {
            actions.add("缩短单次 AI 再练题量，优先保证完成，再逐步增加难度。");
        }
        if (activeDays7d <= 2) {
            actions.add("把复盘任务拆成 10 到 15 分钟的小步任务，先提高连续学习天数。");
        }
        if (actions.isEmpty()) {
            actions.add("保持当前节奏，继续通过课堂提问、针对性练习和迁移题巩固掌握状态。");
        }
        actions.add("当前支持阶段：" + supportStage.get("label") + "，建议按对应支架强度安排后续学习。");
        return actions;
    }

    private String interactionProfile(
            long recentChatMessageCount,
            long recentExerciseCount,
            long recentAiQuestionCount,
            RealtimeStudentStateService.RealtimeSnapshot realtimeSnapshot) {
        if ("LIVE".equals(realtimeSnapshot.dataState())
                && realtimeSnapshot.recentChatQuestions() != null
                && realtimeSnapshot.recentChatQuestions() >= 3) {
            return "即时求助型";
        }
        if ("LIVE".equals(realtimeSnapshot.dataState())
                && realtimeSnapshot.recentAiInteractions() != null
                && realtimeSnapshot.recentAiInteractions() >= 2) {
            return "课堂再练驱动型";
        }
        if (recentChatMessageCount >= recentExerciseCount
                && recentChatMessageCount >= recentAiQuestionCount
                && recentChatMessageCount > 0) {
            return "问答探究型";
        }
        if (recentAiQuestionCount > recentExerciseCount && recentAiQuestionCount > 0) {
            return "AI 再练驱动型";
        }
        if (recentExerciseCount > 0) {
            return "练习巩固型";
        }
        return "等待新近课堂数据";
    }

    private String latestActivityAt(UUID studentId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        select max(activity_time) as latest_activity_at
                        from (
                          select max(created_at) as activity_time
                          from exercise_records
                          where student_id = ?
                          union all
                          select max(generated_at) as activity_time
                          from ai_question_sessions
                          where student_id = ?
                          union all
                          select max(m.created_at) as activity_time
                          from chat_messages m
                          join chat_sessions s on s.id = m.session_id
                          where s.student_id = ?
                            and m.role = 'USER'
                        ) activity_stream
                        """,
                        studentId,
                        studentId,
                        studentId);
        if (rows.isEmpty()) {
            return null;
        }
        return ApiDataMapper.asIsoTime(rows.getFirst().get("latest_activity_at"));
    }

    private Map<String, Object> toRealtimeStateVo(
            RealtimeStudentStateService.RealtimeSnapshot realtimeSnapshot) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("dataState", realtimeSnapshot.dataState());
        state.put("windowMinutes", realtimeSnapshot.windowMinutes());
        state.put("recentChatQuestions", realtimeSnapshot.recentChatQuestions());
        state.put("recentExerciseSubmissions", realtimeSnapshot.recentExerciseSubmissions());
        state.put("recentAiInteractions", realtimeSnapshot.recentAiInteractions());
        state.put("recentWrongCount", realtimeSnapshot.recentWrongCount());
        state.put("recentQuestionAttempts", realtimeSnapshot.recentQuestionAttempts());
        state.put("recentErrorDensity", realtimeSnapshot.recentErrorDensity());
        state.put("hotspotKnowledgePoints", realtimeSnapshot.hotspotKnowledgePoints());
        state.put("signals", realtimeSnapshot.signals());
        return state;
    }

    private double percentage(double numerator, double denominator) {
        if (denominator <= 0D) {
            return 0D;
        }
        return BigDecimal.valueOf((numerator * 100D) / denominator)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private long countOf(String sql, Object... args) {
        Number val = jdbc.queryForObject(sql, Number.class, args);
        return val == null ? 0L : val.longValue();
    }
}
