package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.AiQuestionRecord;
import com.edunexus.api.domain.AiQuestionRecordItem;
import com.edunexus.api.domain.AiQuestionSession;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AiQuestionRepository {

    private static final String SESSION_SELECT =
            """
            select
              s.id,
              s.student_id,
              s.subject,
              s.difficulty,
              s.question_count,
              s.completed,
              s.correct_rate,
              s.score,
              (
                select r.id
                from ai_question_records r
                where r.session_id = s.id
                order by r.submitted_at desc
                limit 1
              ) as record_id,
              s.context_snapshot,
              s.generated_at,
              s.updated_at
            from ai_question_sessions s
            """;

    private static final RowMapper<AiQuestionSession> SESSION_MAPPER =
            (rs, rn) -> {
                Number correctRate = (Number) rs.getObject("correct_rate");
                Number score = (Number) rs.getObject("score");
                return new AiQuestionSession(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("student_id"),
                        rs.getString("subject"),
                        rs.getString("difficulty"),
                        rs.getInt("question_count"),
                        rs.getBoolean("completed"),
                        correctRate == null ? null : correctRate.doubleValue(),
                        score == null ? null : score.intValue(),
                        (UUID) rs.getObject("record_id"),
                        rs.getString("context_snapshot"),
                        ApiDataMapper.toInstant(rs.getTimestamp("generated_at")),
                        ApiDataMapper.toInstant(rs.getTimestamp("updated_at")));
            };

    private static final RowMapper<AiQuestionRecord> RECORD_MAPPER =
            (rs, rn) ->
                    new AiQuestionRecord(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("session_id"),
                            (UUID) rs.getObject("student_id"),
                            rs.getInt("total_questions"),
                            rs.getInt("correct_count"),
                            rs.getInt("total_score"),
                            ApiDataMapper.toInstant(rs.getTimestamp("submitted_at")));

    private final JdbcTemplate jdbc;

    public AiQuestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID createSession(
            UUID studentId,
            String subject,
            String difficulty,
            int questionCount,
            String contextJson) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into ai_question_sessions(id,student_id,subject,difficulty,question_count,context_snapshot) values (?,?,?,?,?,?::jsonb)",
                id,
                studentId,
                subject,
                difficulty,
                questionCount,
                contextJson);
        return id;
    }

    public AiQuestionSession findSession(UUID sessionId) {
        List<AiQuestionSession> rows =
                jdbc.query(SESSION_SELECT + " where s.id=?", SESSION_MAPPER, sessionId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("AI题目会话不存在");
        return rows.getFirst();
    }

    public List<AiQuestionSession> listSessions(
            UUID studentId, String subject, int limit, int offset) {
        StringBuilder where = new StringBuilder(" where s.student_id=?");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        if (subject != null && !subject.isBlank()) {
            where.append(" and s.subject=?");
            args.add(subject);
        }
        args.add(limit);
        args.add(offset);
        return jdbc.query(
                SESSION_SELECT + where + " order by s.generated_at desc limit ? offset ?",
                SESSION_MAPPER,
                args.toArray());
    }

    public long countSessions(UUID studentId, String subject) {
        StringBuilder where = new StringBuilder(" where student_id=?");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        if (subject != null && !subject.isBlank()) {
            where.append(" and subject=?");
            args.add(subject);
        }
        Number val =
                jdbc.queryForObject(
                        "select count(*) from ai_question_sessions" + where,
                        Number.class,
                        args.toArray());
        return val == null ? 0L : val.longValue();
    }

    public void updateSessionQuestionCount(UUID sessionId, int count) {
        jdbc.update(
                "update ai_question_sessions set question_count=?,updated_at=now() where id=?",
                count,
                sessionId);
    }

    public void completeSession(UUID sessionId, double correctRate, int totalScore) {
        jdbc.update(
                "update ai_question_sessions set completed=true,correct_rate=?,score=?,updated_at=now() where id=?",
                correctRate,
                totalScore,
                sessionId);
    }

    public UUID createRecord(UUID sessionId, UUID studentId, int totalQuestions) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into ai_question_records(id,session_id,student_id,total_questions,correct_count,total_score) values (?,?,?,?,?,?)",
                id,
                sessionId,
                studentId,
                totalQuestions,
                0,
                0);
        return id;
    }

    public void updateRecord(UUID recordId, int correctCount, int totalScore) {
        jdbc.update(
                "update ai_question_records set correct_count=?,total_score=? where id=?",
                correctCount,
                totalScore,
                recordId);
    }

    public AiQuestionRecord findRecord(UUID recordId) {
        List<AiQuestionRecord> rows =
                jdbc.query(
                        "select id,session_id,student_id,total_questions,correct_count,total_score,submitted_at from ai_question_records where id=?",
                        RECORD_MAPPER,
                        recordId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("AI题目记录不存在");
        return rows.getFirst();
    }

    public void createRecordItem(
            UUID recordId,
            UUID questionId,
            String userAnswer,
            String correctAnswer,
            boolean isCorrect,
            int score) {
        jdbc.update(
                """
                insert into ai_question_record_items(id,record_id,question_id,user_answer,correct_answer,is_correct,score,analysis)
                values (?,?,?,?,?,?,?,(select analysis from questions where id=?))
                """,
                UUID.randomUUID(),
                recordId,
                questionId,
                userAnswer,
                correctAnswer,
                isCorrect,
                score,
                questionId);
    }

    public List<AiQuestionRecordItem> listAnalysisItems(UUID recordId, UUID studentId) {
        return jdbc.query(
                """
                select
                  i.id, i.record_id, i.question_id, i.user_answer, i.correct_answer, i.is_correct, i.score,
                  coalesce(i.analysis, q.analysis) as analysis,
                  (select s.suggestion from teacher_suggestions s
                   where s.student_id=?
                     and (s.question_id=i.question_id
                          or (s.knowledge_point is not null and q.knowledge_points is not null
                              and jsonb_exists(q.knowledge_points, s.knowledge_point)))
                   order by s.created_at desc limit 1) as teacher_suggestion,
                  i.created_at
                from ai_question_record_items i
                join questions q on q.id=i.question_id
                where i.record_id=?
                order by i.created_at asc
                """,
                (rs, rn) ->
                        new AiQuestionRecordItem(
                                (UUID) rs.getObject("id"),
                                (UUID) rs.getObject("record_id"),
                                (UUID) rs.getObject("question_id"),
                                rs.getString("user_answer"),
                                rs.getString("correct_answer"),
                                rs.getBoolean("is_correct"),
                                rs.getInt("score"),
                                rs.getString("analysis"),
                                ApiDataMapper.toInstant(rs.getTimestamp("created_at"))),
                studentId,
                recordId);
    }
}
