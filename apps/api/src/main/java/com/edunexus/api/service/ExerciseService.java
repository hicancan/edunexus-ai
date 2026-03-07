package com.edunexus.api.service;

import com.edunexus.api.common.AnswerNormalizer;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.domain.ExerciseRecord;
import com.edunexus.api.domain.ExerciseRecordItem;
import com.edunexus.api.domain.Question;
import com.edunexus.api.domain.WeakPoint;
import com.edunexus.api.domain.WrongBookEntry;
import com.edunexus.api.repository.ExerciseRepository;
import com.edunexus.api.repository.QuestionRepository;
import com.edunexus.api.repository.SuggestionRepository;
import com.edunexus.api.repository.WrongBookRepository;
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
public class ExerciseService {

    private final ExerciseRepository exerciseRepo;
    private final QuestionRepository questionRepo;
    private final WrongBookRepository wrongBookRepo;
    private final SuggestionRepository suggestionRepo;
    private final JdbcTemplate jdbc;

    public ExerciseService(
            ExerciseRepository exerciseRepo,
            QuestionRepository questionRepo,
            WrongBookRepository wrongBookRepo,
            SuggestionRepository suggestionRepo,
            JdbcTemplate jdbc) {
        this.exerciseRepo = exerciseRepo;
        this.questionRepo = questionRepo;
        this.wrongBookRepo = wrongBookRepo;
        this.suggestionRepo = suggestionRepo;
        this.jdbc = jdbc;
    }

    public List<Question> listQuestions(String subject, String difficulty, int page, int size) {
        return questionRepo.list(subject, difficulty, size, (page - 1) * size);
    }

    public long countQuestions(String subject, String difficulty) {
        return questionRepo.count(subject, difficulty);
    }

    public record SubmitResult(
            UUID recordId,
            int totalQuestions,
            int correctCount,
            int totalScore,
            List<Map<String, Object>> items) {}

    public SubmitResult submitExercise(
            UUID studentId, List<AnswerItem> answers, Integer timeSpent) {
        UUID recordId =
                exerciseRepo.createRecord(
                        studentId, answers.size(), timeSpent == null ? 0 : timeSpent);
        int correctCount = 0;
        int totalScore = 0;
        String subject = null;
        List<Map<String, Object>> items = new ArrayList<>();

        for (AnswerItem answerItem : answers) {
            Question question = questionRepo.findById(answerItem.questionId());
            if (subject == null) subject = question.subject();
            boolean isCorrect =
                    AnswerNormalizer.isCorrect(
                            question.questionType(),
                            question.correctAnswer(),
                            answerItem.userAnswer());
            int score = isCorrect ? question.score() : 0;
            if (isCorrect) correctCount++;
            totalScore += score;

            String teacherSuggestion =
                    suggestionRepo.fetchByStudentAndQuestion(studentId, answerItem.questionId());
            exerciseRepo.createItem(
                    recordId,
                    answerItem.questionId(),
                    answerItem.userAnswer(),
                    question.correctAnswer(),
                    isCorrect,
                    score,
                    question.analysis(),
                    teacherSuggestion);

            if (!isCorrect) wrongBookRepo.upsert(studentId, answerItem.questionId());

            items.add(
                    Map.of(
                            "questionId", answerItem.questionId().toString(),
                            "userAnswer", answerItem.userAnswer(),
                            "correctAnswer", question.correctAnswer(),
                            "isCorrect", isCorrect,
                            "score", score));
        }
        exerciseRepo.updateRecord(recordId, subject, correctCount, totalScore);
        return new SubmitResult(recordId, answers.size(), correctCount, totalScore, items);
    }

    public void ensureRecordOwner(UUID recordId, UUID studentId) {
        ExerciseRecord record = exerciseRepo.findRecord(recordId);
        if (!studentId.equals(record.studentId())) throw new SecurityException("非资源归属者");
    }

    public List<Map<String, Object>> getAnalysisItems(UUID recordId, UUID studentId) {
        // Returns enriched items - we use a custom query in ExerciseRepository that joins question
        // content
        // The items come from the repository with content/knowledgePoints embedded via the query
        List<ExerciseRecordItem> items = exerciseRepo.listAnalysisItems(recordId, studentId);
        // Enrich with question content via a second pass
        return items.stream()
                .map(
                        item -> {
                            Question q = questionRepo.findById(item.questionId());
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.put("questionId", item.questionId().toString());
                            out.put("content", q.content());
                            out.put("userAnswer", item.userAnswer());
                            out.put("correctAnswer", item.correctAnswer());
                            out.put("isCorrect", item.isCorrect());
                            out.put("analysis", item.analysis());
                            out.put(
                                    "knowledgePoints",
                                    ApiDataMapper.parseNullableStringList(
                                            q.knowledgePointsJson(),
                                            new com.fasterxml.jackson.databind.ObjectMapper()));
                            out.put("teacherSuggestion", item.teacherSuggestion());
                            return out;
                        })
                .toList();
    }

    public List<WrongBookEntry> listWrongQuestions(
            UUID studentId, String status, String subject, int page, int size) {
        return wrongBookRepo.list(studentId, status, subject, size, (page - 1) * size);
    }

    public long countWrongQuestions(UUID studentId, String status, String subject) {
        return wrongBookRepo.count(studentId, status, subject);
    }

    public Question findQuestion(UUID questionId) {
        return questionRepo.findById(questionId);
    }

    public int markWrongQuestionMastered(UUID studentId, UUID questionId) {
        return wrongBookRepo.markMastered(studentId, questionId);
    }

    public List<ExerciseRecord> listRecords(
            UUID studentId, String startDate, String endDate, int page, int size) {
        return exerciseRepo.listRecords(studentId, startDate, endDate, size, (page - 1) * size);
    }

    public long countRecords(UUID studentId, String startDate, String endDate) {
        return exerciseRepo.countRecords(studentId, startDate, endDate);
    }

    public List<WeakPoint> getWeakPoints(UUID studentId) {
        long totalWrong = wrongBookRepo.countActive(studentId);
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                select kp.knowledge_point,coalesce(sum(w.wrong_count),0) as wrong_count
                from wrong_book w
                join questions q on q.id=w.question_id
                join lateral jsonb_array_elements_text(coalesce(q.knowledge_points,'[]'::jsonb)) as kp(knowledge_point) on true
                where w.student_id=? and w.status='ACTIVE'
                group by kp.knowledge_point
                order by coalesce(sum(w.wrong_count),0) desc, kp.knowledge_point asc
                limit 5
                """,
                        studentId);
        return rows.stream()
                .map(
                        row -> {
                            int wrongCount = ApiDataMapper.asInt(row.get("wrong_count"));
                            double errorRate =
                                    totalWrong <= 0
                                            ? 0D
                                            : BigDecimal.valueOf((wrongCount * 100D) / totalWrong)
                                                    .setScale(2, RoundingMode.HALF_UP)
                                                    .doubleValue();
                            return new WeakPoint(
                                    String.valueOf(row.get("knowledge_point")),
                                    wrongCount,
                                    errorRate);
                        })
                .toList();
    }

    public record AnswerItem(UUID questionId, String userAnswer) {}
}
