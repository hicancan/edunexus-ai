package com.edunexus.api.service;

import com.edunexus.api.common.ApiDataMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeStudentStateService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeStudentStateService.class);
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration RETENTION = Duration.ofDays(2);
    private static final int MAX_EVENTS = 200;

    public record RealtimeSnapshot(
            String dataState,
            int windowMinutes,
            Integer recentChatQuestions,
            Integer recentExerciseSubmissions,
            Integer recentAiInteractions,
            Integer recentWrongCount,
            Integer recentQuestionAttempts,
            Double recentErrorDensity,
            List<Map<String, Object>> hotspotKnowledgePoints,
            List<String> signals) {}

    private record RealtimeEvent(
            String type,
            Instant occurredAt,
            Integer totalQuestions,
            Integer wrongCount,
            List<String> knowledgePoints) {}

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RealtimeStudentStateService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void recordChatQuestion(UUID studentId) {
        storeEvent(studentId, new RealtimeEvent("CHAT", Instant.now(), null, null, List.of()));
    }

    public void recordExerciseSubmission(
            UUID studentId, int totalQuestions, int wrongCount, List<String> knowledgePoints) {
        storeEvent(
                studentId,
                new RealtimeEvent(
                        "EXERCISE",
                        Instant.now(),
                        Math.max(0, totalQuestions),
                        Math.max(0, wrongCount),
                        normalizeKnowledgePoints(knowledgePoints)));
    }

    public void recordAiQuestionGeneration(UUID studentId) {
        storeEvent(
                studentId, new RealtimeEvent("AI_GENERATE", Instant.now(), null, null, List.of()));
    }

    public void recordAiQuestionSubmission(
            UUID studentId, int totalQuestions, int wrongCount, List<String> knowledgePoints) {
        storeEvent(
                studentId,
                new RealtimeEvent(
                        "AI_SUBMIT",
                        Instant.now(),
                        Math.max(0, totalQuestions),
                        Math.max(0, wrongCount),
                        normalizeKnowledgePoints(knowledgePoints)));
    }

    public RealtimeSnapshot snapshot(UUID studentId) {
        try {
            List<String> rawEvents =
                    redis.opsForList().range(timelineKey(studentId), 0, MAX_EVENTS - 1);
            if (rawEvents == null || rawEvents.isEmpty()) {
                return new RealtimeSnapshot(
                        "NO_ACTIVITY",
                        (int) WINDOW.toMinutes(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0D,
                        List.of(),
                        List.of("近 10 分钟暂无新的课堂交互，当前画像主要依据历史学习轨迹。"));
            }

            Instant windowStart = Instant.now().minus(WINDOW);
            List<RealtimeEvent> recentEvents =
                    rawEvents.stream()
                            .map(this::parseEvent)
                            .filter(
                                    event ->
                                            event != null
                                                    && !event.occurredAt().isBefore(windowStart))
                            .toList();
            if (recentEvents.isEmpty()) {
                return new RealtimeSnapshot(
                        "NO_ACTIVITY",
                        (int) WINDOW.toMinutes(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0D,
                        List.of(),
                        List.of("近 10 分钟暂无新的课堂交互，当前画像主要依据历史学习轨迹。"));
            }

            int recentChatQuestions = 0;
            int recentExerciseSubmissions = 0;
            int recentAiInteractions = 0;
            int recentWrongCount = 0;
            int recentQuestionAttempts = 0;
            Map<String, Integer> knowledgeHotspots = new LinkedHashMap<>();

            for (RealtimeEvent event : recentEvents) {
                switch (event.type()) {
                    case "CHAT" -> recentChatQuestions++;
                    case "EXERCISE" -> recentExerciseSubmissions++;
                    case "AI_GENERATE", "AI_SUBMIT" -> recentAiInteractions++;
                    default -> {}
                }
                recentWrongCount += safeInt(event.wrongCount());
                recentQuestionAttempts += safeInt(event.totalQuestions());
                int hotspotIncrement = Math.max(1, safeInt(event.wrongCount()));
                for (String knowledgePoint : normalizeKnowledgePoints(event.knowledgePoints())) {
                    knowledgeHotspots.merge(knowledgePoint, hotspotIncrement, Integer::sum);
                }
            }

            double recentErrorDensity =
                    recentQuestionAttempts <= 0
                            ? 0D
                            : (recentWrongCount * 100D) / recentQuestionAttempts;
            List<Map<String, Object>> hotspotList =
                    knowledgeHotspots.entrySet().stream()
                            .sorted(
                                    Comparator.<Map.Entry<String, Integer>>comparingInt(
                                                    Map.Entry::getValue)
                                            .reversed()
                                            .thenComparing(Map.Entry::getKey))
                            .limit(5)
                            .map(
                                    entry -> {
                                        Map<String, Object> row = new LinkedHashMap<>();
                                        row.put("knowledgePoint", entry.getKey());
                                        row.put("eventCount", entry.getValue());
                                        return row;
                                    })
                            .toList();

            List<String> signals = new ArrayList<>();
            if (recentChatQuestions >= 3) {
                signals.add("近 10 分钟已连续发起 " + recentChatQuestions + " 次课堂提问，当前处于高求助状态。");
            }
            if (recentWrongCount >= 3 && recentQuestionAttempts > 0) {
                signals.add("近 10 分钟错误密度为 " + round2(recentErrorDensity) + "%，建议优先放缓节奏并补一次低门槛诊断。");
            }
            if (!hotspotList.isEmpty()) {
                signals.add(
                        "课堂即时卡点集中在「"
                                + hotspotList.getFirst().get("knowledgePoint")
                                + "」，宜先统一复讲再安排分层再练。");
            }
            if (recentAiInteractions >= 2) {
                signals.add("近 10 分钟已多次触发 AI 再练，说明学生正在主动寻求即时支架。");
            }
            if (signals.isEmpty()) {
                signals.add("近 10 分钟学习状态平稳，当前没有明显新的风险波动。");
            }

            return new RealtimeSnapshot(
                    "LIVE",
                    (int) WINDOW.toMinutes(),
                    recentChatQuestions,
                    recentExerciseSubmissions,
                    recentAiInteractions,
                    recentWrongCount,
                    recentQuestionAttempts,
                    round2(recentErrorDensity),
                    hotspotList,
                    signals);
        } catch (DataAccessException ex) {
            log.warn(
                    "realtime_state_unavailable studentId={} message={}",
                    studentId,
                    ex.getMessage());
            return new RealtimeSnapshot(
                    "UNAVAILABLE",
                    (int) WINDOW.toMinutes(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of("近时态课堂状态暂不可用，请检查 Redis 与课堂态链路。"));
        }
    }

    private void storeEvent(UUID studentId, RealtimeEvent event) {
        try {
            String payload =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "type",
                                    event.type(),
                                    "occurredAt",
                                    event.occurredAt().toString(),
                                    "totalQuestions",
                                    event.totalQuestions(),
                                    "wrongCount",
                                    event.wrongCount(),
                                    "knowledgePoints",
                                    normalizeKnowledgePoints(event.knowledgePoints())));
            String key = timelineKey(studentId);
            redis.opsForList().leftPush(key, payload);
            redis.opsForList().trim(key, 0, MAX_EVENTS - 1);
            redis.expire(key, RETENTION);
        } catch (Exception ex) {
            log.warn(
                    "realtime_state_write_failed studentId={} type={} message={}",
                    studentId,
                    event.type(),
                    ex.getMessage());
        }
    }

    private RealtimeEvent parseEvent(String raw) {
        try {
            Map<String, Object> row =
                    objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            return new RealtimeEvent(
                    ApiDataMapper.asString(row.get("type")),
                    Instant.parse(ApiDataMapper.asString(row.get("occurredAt"))),
                    row.get("totalQuestions") == null
                            ? null
                            : ApiDataMapper.asInt(row.get("totalQuestions")),
                    row.get("wrongCount") == null
                            ? null
                            : ApiDataMapper.asInt(row.get("wrongCount")),
                    normalizeKnowledgePoints(
                            ApiDataMapper.parseNullableStringList(
                                    row.get("knowledgePoints"), objectMapper)));
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> normalizeKnowledgePoints(List<String> knowledgePoints) {
        if (knowledgePoints == null || knowledgePoints.isEmpty()) {
            return List.of();
        }
        return knowledgePoints.stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private String timelineKey(UUID studentId) {
        return "analytics:student:" + studentId + ":timeline";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }
}
