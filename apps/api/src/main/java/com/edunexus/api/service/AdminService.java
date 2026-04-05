package com.edunexus.api.service;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ConflictException;
import com.edunexus.api.common.FilenameUtil;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.AdminResource;
import com.edunexus.api.domain.AuditLog;
import com.edunexus.api.domain.DashboardMetrics;
import com.edunexus.api.domain.User;
import com.edunexus.api.repository.AdminResourceRepository;
import com.edunexus.api.repository.AuditRepository;
import com.edunexus.api.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private record StrategyObservation(
            Double avgLatencyMs,
            Double p95LatencyMs,
            long latencySampleCount,
            Double privacyRetentionRate,
            long executionSampleCount,
            Double unitCostIndex,
            Map<String, Long> laneCounts) {}

    private final UserRepository userRepo;
    private final AdminResourceRepository resourceRepo;
    private final AuditRepository auditRepo;
    private final ObjectStorageService objectStorageService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final String runtimeStrategy;

    public AdminService(
            UserRepository userRepo,
            AdminResourceRepository resourceRepo,
            AuditRepository auditRepo,
            ObjectStorageService objectStorageService,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc,
            @Value("${app.runtime-strategy:云边端协同}") String runtimeStrategy) {
        this.userRepo = userRepo;
        this.resourceRepo = resourceRepo;
        this.auditRepo = auditRepo;
        this.objectStorageService = objectStorageService;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.runtimeStrategy = normalizeStrategy(runtimeStrategy);
    }

    public List<User> listUsers(String role, String status, int page, int size) {
        return userRepo.list(role, status, size, (page - 1) * size);
    }

    public long countUsers(String role, String status) {
        return userRepo.count(role, status);
    }

    public User createUser(
            String username, String password, String role, String email, String phone) {
        if (userRepo.existsByUsername(username)) throw new ConflictException("用户名已存在");
        UUID id = userRepo.create(username, passwordEncoder.encode(password), email, phone, role);
        return userRepo.findById(id);
    }

    public User patchUser(UUID userId, String role, String status) {
        User existing = userRepo.findById(userId);
        String finalRole = (role == null || role.isBlank()) ? existing.role() : role;
        String finalStatus = (status == null || status.isBlank()) ? existing.status() : status;
        userRepo.patchRoleStatus(userId, finalRole, finalStatus);
        return userRepo.findById(userId);
    }

    public List<AdminResource> listResources(String resourceType, int page, int size) {
        return resourceRepo.list(resourceType, size, (page - 1) * size);
    }

    public long countResources(String resourceType) {
        return resourceRepo.count(resourceType);
    }

    public record DownloadData(String filename, byte[] bytes) {}

    public DownloadData downloadResource(UUID resourceId) {
        // Try lesson plan
        List<String[]> planRows =
                jdbc.query(
                        "select topic,content_md from lesson_plans where id=? and deleted_at is null",
                        (rs, rn) ->
                                new String[] {rs.getString("topic"), rs.getString("content_md")},
                        resourceId);
        if (!planRows.isEmpty()) {
            String[] row = planRows.getFirst();
            return new DownloadData(
                    FilenameUtil.sanitize(row[0]) + ".md", row[1].getBytes(StandardCharsets.UTF_8));
        }

        // Try document
        List<String[]> docRows =
                jdbc.query(
                        "select filename,storage_path from documents where id=? and deleted_at is null",
                        (rs, rn) ->
                                new String[] {
                                    rs.getString("filename"), rs.getString("storage_path")
                                },
                        resourceId);
        if (!docRows.isEmpty()) {
            String[] row = docRows.getFirst();
            return new DownloadData(
                    FilenameUtil.sanitize(row[0]), objectStorageService.download(row[1]));
        }

        // Try question
        List<String[]> qRows =
                jdbc.query(
                        "select content,analysis from questions where id=? and is_active=true",
                        (rs, rn) ->
                                new String[] {rs.getString("content"), rs.getString("analysis")},
                        resourceId);
        if (!qRows.isEmpty()) {
            String[] row = qRows.getFirst();
            String payload = "题干:\n" + row[0] + "\n\n解析:\n" + row[1];
            return new DownloadData(
                    "question-" + resourceId + ".txt", payload.getBytes(StandardCharsets.UTF_8));
        }

        throw new ResourceNotFoundException("资源不存在");
    }

    public List<AuditLog> listAudits(int page, int size) {
        return auditRepo.list(size, (page - 1) * size);
    }

    public long countAudits() {
        return auditRepo.count();
    }

    public DashboardMetrics getDashboardMetrics() {
        long totalUsers =
                countOf(
                        "select count(*) from users where deleted_at is null and status='ACTIVE'");
        long totalStudents =
                countOf(
                        "select count(*) from users where role='STUDENT' and deleted_at is null and status='ACTIVE'");
        long totalTeachers =
                countOf(
                        "select count(*) from users where role='TEACHER' and deleted_at is null and status='ACTIVE'");
        long totalAdmins =
                countOf(
                        "select count(*) from users where role='ADMIN' and deleted_at is null and status='ACTIVE'");
        long totalChatSessions = countOf("select count(*) from chat_sessions where is_deleted=false");
        long totalChatMessages = countOf("select count(*) from chat_messages");
        long totalExerciseRecords = countOf("select count(*) from exercise_records");
        long totalQuestions = countOf("select count(*) from questions where is_active=true");
        long totalDocuments = countOf("select count(*) from documents where deleted_at is null");
        long totalKnowledgeChunks =
                countOf(
                        "select coalesce(sum((result->>'chunks')::bigint),0) from job_runs where job_type='DOCUMENT_INGEST' and status='SUCCEEDED' and jsonb_exists(result,'chunks')");
        long totalLessonPlans = countOf("select count(*) from lesson_plans where deleted_at is null");
        long totalAiQuestionSessions = countOf("select count(*) from ai_question_sessions");
        long studentChatTurnCount =
                countOf(
                        """
                        select count(*)
                        from audit_logs
                        where action = 'SEND_CHAT_MESSAGE'
                        """);

        long suggestionDispatchCount = countOf("select count(*) from teacher_suggestions");
        long readyDocuments =
                countOf("select count(*) from documents where deleted_at is null and status='READY'");
        long completedAiQuestionSessions =
                countOf("select count(*) from ai_question_sessions where completed=true");
        long totalAuditLogs = countOf("select count(*) from audit_logs");
        long traceableAuditLogs =
                countOf(
                        """
                        select count(*)
                        from audit_logs
                        where detail is not null
                          and nullif(detail->>'traceId', '') is not null
                        """);
        long activeWrongEntries = countOf("select count(*) from wrong_book where status='ACTIVE'");
        long masteredWrongEntries =
                countOf("select count(*) from wrong_book where status='MASTERED'");
        long atRiskStudents =
                countOf("select count(distinct student_id) from wrong_book where status='ACTIVE'");
        long suggestedStudents = countOf("select count(distinct student_id) from teacher_suggestions");
        long riskKnowledgePoints =
                countOf(
                        """
                        select count(distinct kp.knowledge_point)
                        from wrong_book w
                        join questions q on q.id = w.question_id
                        join lateral jsonb_array_elements_text(coalesce(q.knowledge_points,'[]'::jsonb)) as kp(knowledge_point) on true
                        where w.status='ACTIVE'
                        """);
        long suggestedKnowledgePoints =
                countOf(
                        """
                        select count(distinct knowledge_point)
                        from teacher_suggestions
                        where knowledge_point is not null
                          and btrim(knowledge_point) <> ''
                        """);
        long recommendationViewCount =
                countOf(
                        """
                        select count(*)
                        from audit_logs
                        where action = 'VIEW_INTERVENTION_RECOMMENDATIONS'
                        """);
        long adoptedRecommendationViewCount =
                countOf(
                        """
                        select count(*)
                        from audit_logs view_log
                        where view_log.action = 'VIEW_INTERVENTION_RECOMMENDATIONS'
                          and view_log.actor_id is not null
                          and exists (
                            select 1
                            from audit_logs follow_log
                            where follow_log.actor_id = view_log.actor_id
                              and follow_log.action in ('CREATE_SUGGESTION', 'UPDATE_SUGGESTION', 'BULK_CREATE_SUGGESTION')
                              and follow_log.created_at >= view_log.created_at
                              and follow_log.created_at <= view_log.created_at + interval '10 minute'
                          )
                        """);
        long totalChatAuditSamples =
                countOf(
                        """
                        select count(*)
                        from audit_logs
                        where action = 'SEND_CHAT_MESSAGE'
                          and detail is not null
                          and nullif(detail->>'citationCount', '') is not null
                        """);
        long retrievalHitCount =
                countOf(
                        """
                        select count(*)
                        from audit_logs
                        where action = 'SEND_CHAT_MESSAGE'
                          and detail is not null
                          and nullif(detail->>'citationCount', '') is not null
                          and (detail->>'citationCount')::int > 0
                        """);
        long suggestionExecutedCount =
                countOf(
                        """
                        select count(*)
                        from teacher_suggestions s
                        where exists (
                          select 1
                          from exercise_records r
                          where r.student_id = s.student_id
                            and r.created_at > coalesce(s.updated_at, s.created_at)
                            and r.created_at <= coalesce(s.updated_at, s.created_at) + interval '7 day'
                        )
                        """);

        Map<String, StrategyObservation> strategyObservations = loadStrategyObservations();
        StrategyObservation currentObservation = strategyObservations.get(runtimeStrategy);

        List<Map<String, Object>> executionDistribution =
                buildExecutionDistribution(currentObservation);
        List<Map<String, Object>> responseBenchmarks =
                List.of(
                        buildAuditLatencyMetric("课堂即时问答", "SEND_CHAT_MESSAGE", "审计链路"),
                        buildAuditLatencyMetric("AI 个性化出题", "GENERATE_AIQ", "审计链路"),
                        buildAuditLatencyMetric("教师教案生成", "GENERATE_PLAN", "审计链路"),
                        buildJobLatencyMetric("知识入库", "DOCUMENT_INGEST", "作业任务"));

        long responseBenchmarkSamples =
                responseBenchmarks.stream()
                        .mapToLong(metric -> ApiDataMapper.asLong(metric.get("sampleCount")))
                        .sum();

        Double traceCoverageRate = nullablePercentage(traceableAuditLogs, totalAuditLogs);
        Double teacherAdoptionRate =
                recommendationViewCount > 0
                        ? percentage(adoptedRecommendationViewCount, recommendationViewCount)
                        : null;
        Double localRetentionRate =
                currentObservation != null && currentObservation.executionSampleCount() > 0
                        ? currentObservation.privacyRetentionRate()
                        : null;
        Double sensitiveOutboundRate =
                localRetentionRate == null ? null : clamp(100D - localRetentionRate, 0D, 100D);
        Double knowledgeReadinessRate = nullablePercentage(readyDocuments, totalDocuments);
        Double aiCompletionRate =
                nullablePercentage(completedAiQuestionSessions, totalAiQuestionSessions);
        Double wrongBookClosureRate =
                nullablePercentage(masteredWrongEntries, activeWrongEntries + masteredWrongEntries);
        Double suggestionCoverageRate = nullablePercentage(suggestedStudents, atRiskStudents);
        Double retrievalHitRate = nullablePercentage(retrievalHitCount, totalChatAuditSamples);
        Double suggestionExecutionRate =
                nullablePercentage(suggestionExecutedCount, suggestionDispatchCount);
        Double measuredLatency = averageLatency(responseBenchmarks);
        Double p95PeakLatency = peakLatency(responseBenchmarks);
        Double currentUnitCostIndex =
                currentObservation != null && currentObservation.executionSampleCount() > 0
                        ? currentObservation.unitCostIndex()
                        : null;
        long completionCompletedUnits =
                readyDocuments + completedAiQuestionSessions + suggestedStudents;
        long completionUnitSamples = totalDocuments + totalAiQuestionSessions + atRiskStudents;
        Double measuredCompletionRate =
                nullablePercentage(completionCompletedUnits, completionUnitSamples);

        Map<String, Object> governanceSummary = new LinkedHashMap<>();
        governanceSummary.put("traceCoverageRate", traceCoverageRate);
        governanceSummary.put("teacherAdoptionRate", teacherAdoptionRate);
        governanceSummary.put("localRetentionRate", localRetentionRate);
        governanceSummary.put("sensitiveOutboundRate", sensitiveOutboundRate);
        governanceSummary.put("retrievalHitRate", retrievalHitRate);
        governanceSummary.put("suggestionExecutionRate", suggestionExecutionRate);
        governanceSummary.put("auditedActions", totalAuditLogs);
        governanceSummary.put("traceCoverageSamples", totalAuditLogs);
        governanceSummary.put("suggestionDispatchCount", suggestionDispatchCount);
        governanceSummary.put("teacherAdoptionSamples", recommendationViewCount);
        governanceSummary.put("teacherAdoptedCount", adoptedRecommendationViewCount);
        governanceSummary.put("readyDocuments", readyDocuments);
        governanceSummary.put("activeRiskStudents", atRiskStudents);
        governanceSummary.put("studentChatTurnCount", studentChatTurnCount);
        governanceSummary.put("diagnosisInteractionCount", studentChatTurnCount + totalExerciseRecords);
        governanceSummary.put("completionCompletedUnits", completionCompletedUnits);
        governanceSummary.put("completionUnitSamples", completionUnitSamples);
        governanceSummary.put(
                "retentionTaskSamples",
                currentObservation == null ? 0L : currentObservation.executionSampleCount());
        governanceSummary.put("retrievalHitSamples", totalChatAuditSamples);
        governanceSummary.put("retrievalHitCount", retrievalHitCount);
        governanceSummary.put("suggestionExecutionSamples", suggestionDispatchCount);
        governanceSummary.put("suggestionExecutedCount", suggestionExecutedCount);

        List<Map<String, Object>> strategyComparison =
                buildStrategyComparison(
                        strategyObservations,
                        measuredLatency,
                        measuredCompletionRate,
                        completionUnitSamples,
                        localRetentionRate,
                        currentUnitCostIndex);
        List<Map<String, Object>> flowLinkage =
                buildFlowLinkage(
                        readyDocuments,
                        totalDocuments,
                        studentChatTurnCount + totalExerciseRecords,
                        retrievalHitRate,
                        completedAiQuestionSessions,
                        aiCompletionRate,
                        suggestionDispatchCount,
                        teacherAdoptionRate,
                        masteredWrongEntries,
                        wrongBookClosureRate);
        List<Map<String, Object>> interventionOutcomes =
                List.of(
                        outcomeMetric(
                                "知识接入就绪率",
                                knowledgeReadinessRate,
                                90D,
                                "%",
                                totalDocuments,
                                readyDocuments + " / " + totalDocuments + " 份文档已可用于课堂检索"),
                        outcomeMetric(
                                "AI 练习完成率",
                                aiCompletionRate,
                                85D,
                                "%",
                                totalAiQuestionSessions,
                                completedAiQuestionSessions
                                        + " / "
                                        + totalAiQuestionSessions
                                        + " 个 AI 练习会话已闭环"),
                        outcomeMetric(
                                "错题闭环消减率",
                                wrongBookClosureRate,
                                60D,
                                "%",
                                activeWrongEntries + masteredWrongEntries,
                                masteredWrongEntries
                                        + " 道错题已从活跃列表进入掌握状态"),
                        outcomeMetric(
                                "教师建议覆盖率",
                                suggestionCoverageRate,
                                75D,
                                "%",
                                atRiskStudents,
                                suggestedStudents + " 名风险学生已收到教师干预建议"));
        List<Map<String, Object>> experimentComparisons =
                buildExperimentComparisons(
                        strategyObservations,
                        measuredLatency,
                        p95PeakLatency,
                        measuredCompletionRate,
                        completionUnitSamples,
                        responseBenchmarkSamples,
                        retrievalHitRate,
                        totalChatAuditSamples,
                        suggestionExecutionRate,
                        suggestionDispatchCount,
                        sensitiveOutboundRate,
                        teacherAdoptionRate,
                        recommendationViewCount,
                        currentUnitCostIndex);

        return new DashboardMetrics(
                totalUsers,
                totalStudents,
                totalTeachers,
                totalAdmins,
                totalChatSessions,
                totalChatMessages,
                totalExerciseRecords,
                totalQuestions,
                totalDocuments,
                totalKnowledgeChunks,
                totalLessonPlans,
                totalAiQuestionSessions,
                executionDistribution,
                responseBenchmarks,
                strategyComparison,
                governanceSummary,
                interventionOutcomes,
                flowLinkage,
                experimentComparisons);
    }

    private long countOf(String sql) {
        Number val = jdbc.queryForObject(sql, Number.class);
        return val == null ? 0L : val.longValue();
    }

    private long countOf(String sql, Object... args) {
        Number val = jdbc.queryForObject(sql, Number.class, args);
        return val == null ? 0L : val.longValue();
    }

    private List<Map<String, Object>> buildExecutionDistribution(StrategyObservation observation) {
        Map<String, Long> laneCounts = observation == null ? Map.of() : observation.laneCounts();
        long total = observation == null ? 0L : observation.executionSampleCount();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(
                executionMetric(
                        "EDGE",
                        "课堂近端响应",
                        laneCounts.getOrDefault("EDGE", 0L),
                        total,
                        "按真实审计中的 executionLane=EDGE 聚合课堂近端响应任务"));
        items.add(
                executionMetric(
                        "CLOUD",
                        "复杂生成推理",
                        laneCounts.getOrDefault("CLOUD", 0L),
                        total,
                        "按真实审计中的 executionLane=CLOUD 聚合复杂生成与云侧推理任务"));
        items.add(
                executionMetric(
                        "HYBRID",
                        "知识接入与协同",
                        laneCounts.getOrDefault("HYBRID", 0L),
                        total,
                        "按真实审计中的 executionLane=HYBRID 聚合知识接入与协同链路"));
        items.add(
                executionMetric(
                        "HUMAN_IN_LOOP",
                        "教师在环确认",
                        laneCounts.getOrDefault("HUMAN_IN_LOOP", 0L),
                        total,
                        "按真实审计中的 executionLane=HUMAN_IN_LOOP 聚合教师确认链路"));
        return items;
    }

    private Map<String, Object> executionMetric(
            String lane, String label, long taskCount, long totalTasks, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("lane", lane);
        item.put("label", label);
        item.put("taskCount", taskCount);
        item.put("share", percentage(taskCount, totalTasks));
        item.put("sampleCount", taskCount);
        item.put("dataState", totalTasks > 0 ? "MEASURED" : "NO_SAMPLES");
        item.put("description", description);
        return item;
    }

    private List<Map<String, Object>> buildStrategyComparison(
            Map<String, StrategyObservation> strategyObservations,
            Double measuredLatency,
            Double measuredCompletionRate,
            long completionUnitSamples,
            Double localRetentionRate,
            Double currentUnitCostIndex) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String strategy : List.of("全云推理", "边侧优先", "云边端协同")) {
            StrategyObservation observation = strategyObservations.get(strategy);
            Double avgLatencyMs =
                    observation != null && observation.latencySampleCount() > 0
                            ? observation.avgLatencyMs()
                            : (strategy.equals(runtimeStrategy) ? measuredLatency : null);
            Double completionRate =
                    strategy.equals(runtimeStrategy) ? measuredCompletionRate : null;
            Double privacyRetentionRate =
                    observation != null && observation.executionSampleCount() > 0
                            ? observation.privacyRetentionRate()
                            : (strategy.equals(runtimeStrategy) ? localRetentionRate : null);
            Double unitCostIndex =
                    observation != null && observation.executionSampleCount() > 0
                            ? observation.unitCostIndex()
                            : (strategy.equals(runtimeStrategy) ? currentUnitCostIndex : null);
            String basis =
                    observation == null
                            ? "该策略尚无真实采样，请按相同班级与负载运行后再比较。"
                            : strategy.equals(runtimeStrategy)
                                    ? "基于当前运行策略的真实审计与执行样本。"
                                    : "基于历史真实采样结果，与当前策略按同口径展示。";
            long sampleCount =
                    observation != null && observation.executionSampleCount() > 0
                            ? observation.executionSampleCount()
                            : strategy.equals(runtimeStrategy)
                                    ? Math.max(
                                            observation == null ? 0L : observation.latencySampleCount(),
                                            completionUnitSamples)
                                    : 0L;
            rows.add(
                    strategyMetric(
                            strategy,
                            avgLatencyMs,
                            completionRate,
                            privacyRetentionRate,
                            unitCostIndex,
                            sampleCount,
                            basis));
        }
        return rows;
    }

    private List<Map<String, Object>> buildFlowLinkage(
            long readyDocuments,
            long totalDocuments,
            long diagnosisInteractions,
            Double retrievalHitRate,
            long completedAiQuestionSessions,
            Double aiCompletionRate,
            long suggestionDispatchCount,
            Double teacherAdoptionRate,
            long masteredWrongEntries,
            Double wrongBookClosureRate) {
        return List.of(
                flowMetric(
                        "PRE_CLASS",
                        "课前知识准备",
                        readyDocuments,
                        nullablePercentage(readyDocuments, totalDocuments),
                        "教学资料已完成切片入库，可直接为课堂问答与生成提供证据底座"),
                flowMetric(
                        "IN_CLASS",
                        "课中即时诊断",
                        diagnosisInteractions,
                        retrievalHitRate,
                        "课堂提问与练习共同构成即时诊断入口，检索命中率可反映证据链可靠性"),
                flowMetric(
                        "PRACTICE",
                        "个性化再练与画像更新",
                        completedAiQuestionSessions,
                        aiCompletionRate,
                        "AI 再练完成后才会真正形成画像刷新与后续支持依据"),
                flowMetric(
                        "TEACHER_LOOP",
                        "教师在环确认",
                        suggestionDispatchCount,
                        teacherAdoptionRate,
                        "建议必须经过教师查看与确认后进入正式教学干预链路"),
                flowMetric(
                        "POST_CLASS",
                        "课后错题闭环",
                        masteredWrongEntries,
                        wrongBookClosureRate,
                        "错题由活跃状态进入掌握状态，代表支持链路真正转化为学习结果"));
    }

    private Map<String, Object> flowMetric(
            String stage, String label, long count, Double rate, String insight) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stage", stage);
        row.put("label", label);
        row.put("count", count);
        row.put("rate", rate);
        row.put("insight", insight);
        row.put("dataState", dataState(rate));
        return row;
    }

    private List<Map<String, Object>> buildExperimentComparisons(
            Map<String, StrategyObservation> strategyObservations,
            Double measuredLatency,
            Double p95PeakLatency,
            Double measuredCompletionRate,
            long completionUnitSamples,
            long latencySampleCount,
            Double retrievalHitRate,
            long retrievalSampleCount,
            Double suggestionExecutionRate,
            long suggestionExecutionSampleCount,
            Double sensitiveOutboundRate,
            Double teacherAdoptionRate,
            long teacherAdoptionSampleCount,
            Double currentUnitCostIndex) {
        StrategyObservation baselineObservation = strategyObservations.get("全云推理");
        StrategyObservation currentObservation = strategyObservations.get(runtimeStrategy);

        Double currentLatency =
                currentObservation != null && currentObservation.latencySampleCount() > 0
                        ? currentObservation.avgLatencyMs()
                        : measuredLatency;
        Double currentP95 =
                currentObservation != null && currentObservation.latencySampleCount() > 0
                        ? currentObservation.p95LatencyMs()
                        : p95PeakLatency;
        long currentLatencySamples =
                currentObservation != null && currentObservation.latencySampleCount() > 0
                        ? currentObservation.latencySampleCount()
                        : latencySampleCount;
        Double baselineLatency =
                baselineObservation == null ? null : baselineObservation.avgLatencyMs();
        Double baselineP95 = baselineObservation == null ? null : baselineObservation.p95LatencyMs();
        Double baselineSensitiveOutboundRate =
                baselineObservation == null || baselineObservation.privacyRetentionRate() == null
                        ? null
                        : clamp(100D - baselineObservation.privacyRetentionRate(), 0D, 100D);
        Double currentSensitiveOutboundRate =
                currentObservation != null && currentObservation.privacyRetentionRate() != null
                        ? clamp(100D - currentObservation.privacyRetentionRate(), 0D, 100D)
                        : sensitiveOutboundRate;
        long currentExecutionSamples =
                currentObservation != null && currentObservation.executionSampleCount() > 0
                        ? currentObservation.executionSampleCount()
                        : nonNullSignalCount(currentSensitiveOutboundRate);

        return List.of(
                experimentMetric(
                        "系统指标",
                        "平均响应时延",
                        baselineLatency,
                        currentLatency,
                        "ms",
                        "lower",
                        currentLatencySamples,
                        baselineObservation == null
                                ? "当前仅展示真实协同策略样本；全云基线尚未单独采样。"
                                : "基于全云基线与当前运行策略的真实时延样本进行同口径对比。"),
                experimentMetric(
                        "系统指标",
                        "峰值时延(P95)",
                        baselineP95,
                        currentP95,
                        "ms",
                        "lower",
                        currentLatencySamples,
                        baselineObservation == null
                                ? "当前仅采到协同策略的 P95 时延峰值，基线待补采。"
                                : "取关键教学场景 P95 时延中的峰值，验证课堂高峰期稳定性。"),
                experimentMetric(
                        "系统指标",
                        "任务完成率",
                        null,
                        measuredCompletionRate,
                        "%",
                        "higher",
                        completionUnitSamples,
                        "按知识接入文档、AI 练习会话与风险学生建议覆盖的真实完成单元加权统计"),
                experimentMetric(
                        "教育效果",
                        "检索命中率",
                        null,
                        retrievalHitRate,
                        "%",
                        "higher",
                        retrievalSampleCount,
                        "由课堂问答中 citationCount > 0 的链路占比计算"),
                experimentMetric(
                        "教育效果",
                        "建议执行转化率",
                        null,
                        suggestionExecutionRate,
                        "%",
                        "higher",
                        suggestionExecutionSampleCount,
                        "统计教师建议发出后 7 天内是否触发后续练习"),
                experimentMetric(
                        "治理指标",
                        "教师采纳率",
                        null,
                        teacherAdoptionRate,
                        "%",
                        "higher",
                        teacherAdoptionSampleCount,
                        "基于教师查看建议与正式下发记录计算的人机协同采纳情况"),
                experimentMetric(
                        "治理指标",
                        "敏感数据外发比例",
                        baselineSensitiveOutboundRate,
                        currentSensitiveOutboundRate,
                        "%",
                        "lower",
                        currentExecutionSamples,
                        baselineObservation == null
                                ? "基线策略未采样，当前仅展示按真实执行落点折算的外发比例。"
                                : "根据真实执行落点折算敏感字段外发压力，越低越好。"),
                experimentMetric(
                        "治理指标",
                        "单任务调用成本",
                        baselineObservation == null ? null : baselineObservation.unitCostIndex(),
                        currentObservation != null && currentObservation.unitCostIndex() != null
                                ? currentObservation.unitCostIndex()
                                : currentUnitCostIndex,
                        "index",
                        "lower",
                        currentExecutionSamples,
                        baselineObservation == null
                                ? "当前仅展示真实协同链路的单位成本指数，基线待采样。"
                                : "按真实边侧/云侧/协同链路权重折算的任务成本指数。"));
    }

    private Map<String, StrategyObservation> loadStrategyObservations() {
        Map<String, StrategyObservation> observations = new LinkedHashMap<>();
        String strategyExpr = strategySqlExpr();

        jdbc.query(
                """
                select
                  %s as runtime_strategy,
                  coalesce(avg((detail->>'latencyMs')::numeric), 0) as avg_latency_ms,
                  coalesce(percentile_cont(0.95) within group (order by (detail->>'latencyMs')::numeric), 0) as p95_latency_ms,
                  count(*) as sample_count
                from audit_logs
                where detail is not null
                  and nullif(detail->>'latencyMs', '') is not null
                group by %s
                """
                        .formatted(strategyExpr, strategyExpr),
                rs -> {
                    String strategy = normalizeStrategy(rs.getString("runtime_strategy"));
                    observations.put(
                            strategy,
                            new StrategyObservation(
                                    rs.getLong("sample_count") > 0
                                            ? round2(rs.getDouble("avg_latency_ms"))
                                            : null,
                                    rs.getLong("sample_count") > 0
                                            ? round2(rs.getDouble("p95_latency_ms"))
                                            : null,
                                    rs.getLong("sample_count"),
                                    null,
                                    0L,
                                    null,
                                    Map.of()));
                });

        Map<String, Map<String, Long>> laneCountsByStrategy = new LinkedHashMap<>();
        jdbc.query(
                """
                select
                  %s as runtime_strategy,
                  coalesce(nullif(detail->>'executionLane', ''), 'UNKNOWN') as execution_lane,
                  count(*) as sample_count
                from audit_logs
                where detail is not null
                  and nullif(detail->>'executionLane', '') is not null
                group by %s,
                         coalesce(nullif(detail->>'executionLane', ''), 'UNKNOWN')
                """
                        .formatted(strategyExpr, strategyExpr),
                rs -> {
                    String strategy = normalizeStrategy(rs.getString("runtime_strategy"));
                    laneCountsByStrategy
                            .computeIfAbsent(strategy, ignored -> new LinkedHashMap<>())
                            .put(
                                    rs.getString("execution_lane"),
                                    rs.getLong("sample_count"));
                });

        for (Map.Entry<String, Map<String, Long>> entry : laneCountsByStrategy.entrySet()) {
            String strategy = entry.getKey();
            Map<String, Long> laneCounts = entry.getValue();
            long executionSampleCount = laneCounts.values().stream().mapToLong(Long::longValue).sum();
            StrategyObservation existing = observations.get(strategy);
            observations.put(
                    strategy,
                    new StrategyObservation(
                            existing == null ? null : existing.avgLatencyMs(),
                            existing == null ? null : existing.p95LatencyMs(),
                            existing == null ? 0L : existing.latencySampleCount(),
                            localRetentionRate(laneCounts),
                            executionSampleCount,
                            weightedUnitCostIndex(laneCounts),
                            Map.copyOf(laneCounts)));
        }

        return observations;
    }

    private String strategySqlExpr() {
        String escaped = runtimeStrategy.replace("'", "''");
        return "coalesce(nullif(detail->>'runtimeStrategy', ''), '" + escaped + "')";
    }

    private Map<String, Object> experimentMetric(
            String category,
            String metric,
            Double baselineValue,
            Double currentValue,
            String unit,
            String betterDirection,
            long sampleCount,
            String evidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("category", category);
        row.put("metric", metric);
        row.put("baselineValue", baselineValue);
        row.put("currentValue", currentValue);
        row.put("unit", unit);
        row.put("betterDirection", betterDirection);
        row.put("delta", delta(baselineValue, currentValue, betterDirection));
        row.put("evidence", evidence);
        row.put("sampleCount", sampleCount);
        row.put("dataState", dataState(currentValue, sampleCount));
        return row;
    }

    private Map<String, Object> strategyMetric(
            String strategy,
            Double avgLatencyMs,
            Double completionRate,
            Double privacyRetentionRate,
            Double unitCostIndex,
            long sampleCount,
            String basis) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("strategy", strategy);
        row.put("avgLatencyMs", avgLatencyMs);
        row.put("completionRate", completionRate);
        row.put("privacyRetentionRate", privacyRetentionRate);
        row.put("unitCostIndex", unitCostIndex);
        row.put("sampleCount", sampleCount);
        row.put("basis", basis);
        row.put(
                "dataState",
                avgLatencyMs == null
                                && completionRate == null
                                && privacyRetentionRate == null
                                && unitCostIndex == null
                                && sampleCount <= 0
                        ? "NO_SAMPLES"
                        : "MEASURED");
        return row;
    }

    private Map<String, Object> outcomeMetric(
            String label, Double value, double target, String unit, long sampleCount, String insight) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", value);
        row.put("target", target);
        row.put("unit", unit);
        row.put("insight", insight);
        row.put("dataState", dataState(value, sampleCount));
        return row;
    }

    private Map<String, Object> buildAuditLatencyMetric(
            String scene, String action, String source) {
        return jdbc.query(
                        """
                        select
                          coalesce(avg((detail->>'latencyMs')::numeric), 0) as avg_latency_ms,
                          coalesce(percentile_cont(0.95) within group (order by (detail->>'latencyMs')::numeric), 0) as p95_latency_ms,
                          count(*) as sample_count
                        from audit_logs
                        where action = ?
                          and detail is not null
                          and nullif(detail->>'latencyMs', '') is not null
                        """,
                        (rs, rn) ->
                                latencyMetric(
                                        scene,
                                        rs.getDouble("avg_latency_ms"),
                                        rs.getDouble("p95_latency_ms"),
                                        rs.getLong("sample_count"),
                                        source),
                        action)
                .stream()
                .findFirst()
                .orElse(latencyMetric(scene, 0D, 0D, 0L, source));
    }

    private Map<String, Object> buildJobLatencyMetric(String scene, String jobType, String source) {
        return jdbc.query(
                        """
                        select
                          coalesce(avg(extract(epoch from (finished_at - started_at)) * 1000), 0) as avg_latency_ms,
                          coalesce(percentile_cont(0.95) within group (order by extract(epoch from (finished_at - started_at)) * 1000), 0) as p95_latency_ms,
                          count(*) as sample_count
                        from job_runs
                        where job_type = ?
                          and started_at is not null
                          and finished_at is not null
                        """,
                        (rs, rn) ->
                                latencyMetric(
                                        scene,
                                        rs.getDouble("avg_latency_ms"),
                                        rs.getDouble("p95_latency_ms"),
                                        rs.getLong("sample_count"),
                                        source),
                        jobType)
                .stream()
                .findFirst()
                .orElse(latencyMetric(scene, 0D, 0D, 0L, source));
    }

    private Map<String, Object> latencyMetric(
            String scene, double avgLatencyMs, double p95LatencyMs, long sampleCount, String source) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scene", scene);
        row.put("avgLatencyMs", sampleCount > 0 ? round2(avgLatencyMs) : null);
        row.put("p95LatencyMs", sampleCount > 0 ? round2(p95LatencyMs) : null);
        row.put("sampleCount", sampleCount);
        row.put("source", source);
        row.put("dataState", sampleCount > 0 ? "MEASURED" : "NO_SAMPLES");
        return row;
    }

    private Double averageLatency(List<Map<String, Object>> metrics) {
        double total = 0D;
        int count = 0;
        for (Map<String, Object> metric : metrics) {
            Number latencyNumber = (Number) metric.get("avgLatencyMs");
            Number sampleCountNumber = (Number) metric.get("sampleCount");
            double latency = latencyNumber == null ? 0D : latencyNumber.doubleValue();
            long sampleCount = sampleCountNumber == null ? 0L : sampleCountNumber.longValue();
            if (sampleCount <= 0 || latency <= 0) {
                continue;
            }
            total += latency;
            count++;
        }
        return count == 0 ? null : round2(total / count);
    }

    private Double peakLatency(List<Map<String, Object>> metrics) {
        double max = 0D;
        for (Map<String, Object> metric : metrics) {
            Number p95Number = (Number) metric.get("p95LatencyMs");
            Number sampleCountNumber = (Number) metric.get("sampleCount");
            long sampleCount = sampleCountNumber == null ? 0L : sampleCountNumber.longValue();
            double p95Latency = p95Number == null ? 0D : p95Number.doubleValue();
            if (sampleCount <= 0 || p95Latency <= 0D) {
                continue;
            }
            max = Math.max(max, p95Latency);
        }
        return max <= 0D ? null : round2(max);
    }

    private Double weightedUnitCostIndex(
            long edgeTasks, long cloudTasks, long hybridTasks, long humanLoopTasks) {
        double totalTasks = edgeTasks + cloudTasks + hybridTasks + humanLoopTasks;
        if (totalTasks <= 0D) {
            return null;
        }
        double weightedCost =
                (edgeTasks * 0.58D)
                        + (cloudTasks * 1.42D)
                        + (hybridTasks * 0.96D)
                        + (humanLoopTasks * 0.72D);
        return round2((weightedCost / totalTasks) * 100D);
    }

    private Double weightedUnitCostIndex(Map<String, Long> laneCounts) {
        return weightedUnitCostIndex(
                laneCounts.getOrDefault("EDGE", 0L),
                laneCounts.getOrDefault("CLOUD", 0L),
                laneCounts.getOrDefault("HYBRID", 0L),
                laneCounts.getOrDefault("HUMAN_IN_LOOP", 0L));
    }

    private Double localRetentionRate(Map<String, Long> laneCounts) {
        long edgeTasks = laneCounts.getOrDefault("EDGE", 0L);
        long cloudTasks = laneCounts.getOrDefault("CLOUD", 0L);
        long hybridTasks = laneCounts.getOrDefault("HYBRID", 0L);
        long humanLoopTasks = laneCounts.getOrDefault("HUMAN_IN_LOOP", 0L);
        long totalTasks = edgeTasks + cloudTasks + hybridTasks + humanLoopTasks;
        if (totalTasks <= 0L) {
            return null;
        }
        double localObserved = edgeTasks + humanLoopTasks + (hybridTasks * 0.65D);
        return percentage(localObserved, totalTasks);
    }

    private Double averageOf(Double... values) {
        double total = 0D;
        int count = 0;
        for (Double value : values) {
            if (value == null) {
                continue;
            }
            total += value;
            count++;
        }
        return count == 0 ? null : round2(total / count);
    }

    private double percentage(double numerator, double denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return round2((numerator * 100D) / denominator);
    }

    private Double nullablePercentage(double numerator, double denominator) {
        if (denominator <= 0D) {
            return null;
        }
        return percentage(numerator, denominator);
    }

    private Double delta(Double baselineValue, Double currentValue, String betterDirection) {
        if (baselineValue == null || currentValue == null) {
            return null;
        }
        return "lower".equals(betterDirection)
                ? round2(baselineValue - currentValue)
                : round2(currentValue - baselineValue);
    }

    private long nonNullSignalCount(Double value) {
        return value == null ? 0L : 1L;
    }

    private long signalCount(Double... values) {
        long count = 0L;
        for (Double value : values) {
            if (value != null) {
                count++;
            }
        }
        return count;
    }

    private String dataState(Double value) {
        return value == null ? "NO_SAMPLES" : "MEASURED";
    }

    private String dataState(Double value, long sampleCount) {
        return value == null || sampleCount <= 0 ? "NO_SAMPLES" : "MEASURED";
    }

    private String normalizeStrategy(String rawStrategy) {
        if (rawStrategy == null || rawStrategy.isBlank()) {
            return "云边端协同";
        }
        String normalized = rawStrategy.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("全云") || normalized.contains("cloud")) {
            return "全云推理";
        }
        if (normalized.contains("边侧") || normalized.contains("edge")) {
            return "边侧优先";
        }
        if (normalized.contains("云边") || normalized.contains("hybrid")) {
            return "云边端协同";
        }
        return rawStrategy.trim();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, round2(value)));
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
