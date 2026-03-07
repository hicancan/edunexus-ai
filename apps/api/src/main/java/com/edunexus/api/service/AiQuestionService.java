package com.edunexus.api.service;

import com.edunexus.api.common.AnswerNormalizer;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.DependencyException;
import com.edunexus.api.common.ErrorCode;
import com.edunexus.api.domain.AiQuestionRecordItem;
import com.edunexus.api.domain.AiQuestionSession;
import com.edunexus.api.domain.Question;
import com.edunexus.api.repository.AiQuestionRepository;
import com.edunexus.api.repository.QuestionRepository;
import com.edunexus.api.repository.SuggestionRepository;
import com.edunexus.api.repository.WrongBookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class AiQuestionService {

    private final AiQuestionRepository aiqRepo;
    private final QuestionRepository questionRepo;
    private final WrongBookRepository wrongBookRepo;
    private final SuggestionRepository suggestionRepo;
    private final AiClient aiClient;
    private final VoMapper voMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public AiQuestionService(
            AiQuestionRepository aiqRepo,
            QuestionRepository questionRepo,
            WrongBookRepository wrongBookRepo,
            SuggestionRepository suggestionRepo,
            AiClient aiClient,
            VoMapper voMapper,
            ObjectMapper objectMapper,
            JdbcTemplate jdbc) {
        this.aiqRepo = aiqRepo;
        this.questionRepo = questionRepo;
        this.wrongBookRepo = wrongBookRepo;
        this.suggestionRepo = suggestionRepo;
        this.aiClient = aiClient;
        this.voMapper = voMapper;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public record GenerateResult(UUID sessionId, List<Map<String, Object>> questions) {}

    public GenerateResult generateQuestions(
            UUID studentId,
            int count,
            String subject,
            String difficulty,
            List<String> conceptTags,
            String traceId,
            String idempotencyKey) {
        List<Map<String, Object>> weaknessProfile =
                jdbc.queryForList(
                        """
                select q.knowledge_points,w.wrong_count from wrong_book w
                join questions q on q.id=w.question_id
                where w.student_id=? and w.status='ACTIVE'
                order by w.last_wrong_time desc limit 20
                """,
                        studentId);
        List<Map<String, Object>> suggestions =
                jdbc.queryForList(
                        "select suggestion,knowledge_point from teacher_suggestions where student_id=? order by created_at desc limit 10",
                        studentId);

        UUID sessionId =
                aiqRepo.createSession(
                        studentId,
                        subject,
                        difficulty,
                        count,
                        toJson(
                                Map.of(
                                        "conceptTags",
                                                conceptTags == null ? List.of() : conceptTags,
                                        "weaknessProfile", weaknessProfile,
                                        "teacherSuggestions", suggestions)));

        Map<String, Object> aiBody = new java.util.HashMap<>();
        aiBody.put("traceId", traceId);
        aiBody.put("studentId", studentId.toString());
        aiBody.put("count", count);
        aiBody.put("subject", subject);
        aiBody.put("difficulty", difficulty);
        aiBody.put("conceptTags", conceptTags == null ? List.of() : conceptTags);
        // Serialize as JSON strings — AiClient passes these as proto string fields;
        // the Python servicer will JSON-parse them back into lists for the prompt.
        aiBody.put("weaknessProfile", toJson(weaknessProfile));
        aiBody.put("teacherSuggestions", toJson(suggestions));
        aiBody.put("idempotencyKey", idempotencyKey == null ? "" : idempotencyKey);

        Map<String, Object> aiResult = aiClient.generateQuestions(aiBody);
        List<Map<String, Object>> generated =
                ApiDataMapper.parseObjectList(aiResult.get("questions"), objectMapper);
        if (generated.isEmpty()) {
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 未生成任何题目");
        }

        List<Map<String, Object>> questionVos = new ArrayList<>();
        for (Map<String, Object> gen : generated) {
            String questionType = ApiDataMapper.asString(gen.get("question_type"));
            String content = ApiDataMapper.asString(gen.get("content"));
            String correctAnswer = ApiDataMapper.asString(gen.get("correct_answer"));
            validateGeneratedQuestion(questionType, content, correctAnswer);

            Map<String, String> options = parseOptions(gen, questionType);
            String normalizedCorrectAnswer =
                    validateCorrectAnswer(questionType, correctAnswer, options);
            List<String> knowledgePoints = parseKnowledgePoints(gen);
            String explanation = ApiDataMapper.asString(gen.get("explanation"));

            UUID questionId =
                    questionRepo.createAiGenerated(
                            subject,
                            questionType,
                            difficulty,
                            content,
                            toJson(options),
                            normalizedCorrectAnswer,
                            explanation == null ? "" : explanation,
                            toJson(knowledgePoints),
                            sessionId,
                            studentId);
            questionVos.add(voMapper.toQuestionVoForStudent(questionRepo.findById(questionId)));
        }

        if (questionVos.isEmpty()) {
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 未生成有效题目");
        }
        aiqRepo.updateSessionQuestionCount(sessionId, questionVos.size());
        return new GenerateResult(sessionId, questionVos);
    }

    public List<AiQuestionSession> listSessions(
            UUID studentId, String subject, int page, int size) {
        return aiqRepo.listSessions(studentId, subject, size, (page - 1) * size);
    }

    public long countSessions(UUID studentId, String subject) {
        return aiqRepo.countSessions(studentId, subject);
    }

    public void ensureSessionOwner(UUID sessionId, UUID studentId) {
        AiQuestionSession session = aiqRepo.findSession(sessionId);
        if (!studentId.equals(session.studentId())) throw new SecurityException("非资源归属者");
    }

    public void ensureRecordOwner(UUID recordId, UUID studentId) {
        var record = aiqRepo.findRecord(recordId);
        if (!studentId.equals(record.studentId())) throw new SecurityException("非资源归属者");
    }

    public record SubmitResult(
            UUID recordId,
            UUID sessionId,
            int totalQuestions,
            int correctCount,
            int totalScore,
            List<Map<String, Object>> items) {}

    public SubmitResult submitQuestions(UUID sessionId, UUID studentId, List<AnswerItem> answers) {
        UUID recordId = aiqRepo.createRecord(sessionId, studentId, answers.size());
        int correctCount = 0;
        int totalScore = 0;
        List<Map<String, Object>> items = new ArrayList<>();

        for (AnswerItem answerItem : answers) {
            Question question = questionRepo.findByIdInSession(answerItem.questionId(), sessionId);
            boolean isCorrect =
                    AnswerNormalizer.isCorrect(
                            question.questionType(),
                            question.correctAnswer(),
                            answerItem.userAnswer());
            int score = isCorrect ? question.score() : 0;
            if (isCorrect) correctCount++;
            totalScore += score;

            aiqRepo.createRecordItem(
                    recordId,
                    answerItem.questionId(),
                    answerItem.userAnswer(),
                    question.correctAnswer(),
                    isCorrect,
                    score);

            items.add(
                    Map.of(
                            "questionId", answerItem.questionId().toString(),
                            "userAnswer", answerItem.userAnswer(),
                            "correctAnswer", question.correctAnswer(),
                            "isCorrect", isCorrect,
                            "score", score));
        }

        aiqRepo.updateRecord(recordId, correctCount, totalScore);
        double rate =
                answers.isEmpty()
                        ? 0D
                        : BigDecimal.valueOf((double) correctCount * 100D / (double) answers.size())
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue();
        aiqRepo.completeSession(sessionId, rate, totalScore);
        return new SubmitResult(
                recordId, sessionId, answers.size(), correctCount, totalScore, items);
    }

    public List<Map<String, Object>> getAnalysisItems(UUID recordId, UUID studentId) {
        List<AiQuestionRecordItem> items = aiqRepo.listAnalysisItems(recordId, studentId);
        return items.stream()
                .map(
                        item -> {
                            Question q = questionRepo.findById(item.questionId());
                            String teacherSuggestion =
                                    suggestionRepo.fetchByStudentAndQuestion(
                                            studentId, item.questionId());
                            return voMapper.toAiQuestionRecordItemVo(
                                    item, q.content(), q.knowledgePointsJson(), teacherSuggestion);
                        })
                .toList();
    }

    private void validateGeneratedQuestion(
            String questionType, String content, String correctAnswer) {
        if (questionType == null || questionType.isBlank())
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 题目缺少 question_type");
        if (content == null || content.isBlank())
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 题目缺少 content");
        if (correctAnswer == null || correctAnswer.isBlank())
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 题目缺少 correct_answer");
        if (!"SINGLE_CHOICE".equals(questionType)
                && !"MULTIPLE_CHOICE".equals(questionType)
                && !"SHORT_ANSWER".equals(questionType))
            throw new DependencyException(
                    ErrorCode.AI_OUTPUT_INVALID, "AI 题目 question_type 非法: " + questionType);
    }

    private Map<String, String> parseOptions(Map<String, Object> gen, String questionType) {
        Object optionsPayload = gen.get("options");
        if (!(optionsPayload instanceof Map<?, ?> rawOptions))
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 题目 options 格式异常");
        Map<String, String> options = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : rawOptions.entrySet()) {
            String key = String.valueOf(e.getKey());
            String val = e.getValue() == null ? "" : String.valueOf(e.getValue());
            if (!key.isBlank() && !val.isBlank()) options.put(key, val);
        }
        if (!"SHORT_ANSWER".equals(questionType) && options.isEmpty())
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 题目 options 为空");
        return options;
    }

    private String validateCorrectAnswer(
            String questionType, String correctAnswer, Map<String, String> options) {
        String normalized = AnswerNormalizer.normalizeForComparison(questionType, correctAnswer);
        if (normalized.isBlank())
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 题目 correct_answer 为空");

        if ("SHORT_ANSWER".equals(questionType)) {
            return normalized;
        }

        if ("MULTIPLE_CHOICE".equals(questionType)) {
            if (normalized.length() < 2)
                throw new DependencyException(
                        ErrorCode.AI_OUTPUT_INVALID, "AI 多选题 correct_answer 至少包含两个选项");
            for (int i = 0; i < normalized.length(); i++) {
                String optionKey = String.valueOf(normalized.charAt(i));
                if (!options.containsKey(optionKey))
                    throw new DependencyException(
                            ErrorCode.AI_OUTPUT_INVALID, "AI 多选题答案缺少选项: " + optionKey);
            }
            return normalized;
        }

        if (!options.containsKey(normalized))
            throw new DependencyException(
                    ErrorCode.AI_OUTPUT_INVALID, "AI 题目 correct_answer 不存在于 options");
        return normalized;
    }

    private List<String> parseKnowledgePoints(Map<String, Object> gen) {
        Object kpPayload = gen.get("knowledge_points");
        if (!(kpPayload instanceof List<?> rawKp))
            throw new DependencyException(
                    ErrorCode.AI_OUTPUT_INVALID, "AI 题目 knowledge_points 格式异常");
        List<String> kps =
                rawKp.stream()
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
        if (kps.isEmpty())
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 题目 knowledge_points 为空");
        return kps;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }

    public record AnswerItem(UUID questionId, String userAnswer) {}
}
