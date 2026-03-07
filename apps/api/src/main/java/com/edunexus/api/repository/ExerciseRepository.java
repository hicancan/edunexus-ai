package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.ExerciseRecord;
import com.edunexus.api.domain.ExerciseRecordItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ExerciseRepository {

    private static final RowMapper<ExerciseRecord> RECORD_MAPPER =
            (rs, rn) ->
                    new ExerciseRecord(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("student_id"),
                            rs.getString("subject"),
                            rs.getInt("total_questions"),
                            rs.getInt("correct_count"),
                            rs.getInt("total_score"),
                            rs.getInt("time_spent"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")));

    private static final RowMapper<ExerciseRecordItem> ITEM_MAPPER =
            (rs, rn) ->
                    new ExerciseRecordItem(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("record_id"),
                            (UUID) rs.getObject("question_id"),
                            rs.getString("user_answer"),
                            rs.getString("correct_answer"),
                            rs.getBoolean("is_correct"),
                            rs.getInt("score"),
                            rs.getString("analysis"),
                            rs.getString("teacher_suggestion"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public ExerciseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID createRecord(UUID studentId, int totalQuestions, int timeSpent) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into exercise_records(id,student_id,subject,total_questions,correct_count,total_score,time_spent) values (?,?,?,?,?,?,?)",
                id,
                studentId,
                null,
                totalQuestions,
                0,
                0,
                timeSpent);
        return id;
    }

    public void updateRecord(UUID recordId, String subject, int correctCount, int totalScore) {
        jdbc.update(
                "update exercise_records set subject=?,correct_count=?,total_score=? where id=?",
                subject,
                correctCount,
                totalScore,
                recordId);
    }

    public ExerciseRecord findRecord(UUID recordId) {
        List<ExerciseRecord> rows =
                jdbc.query(
                        "select id,student_id,subject,total_questions,correct_count,total_score,time_spent,created_at from exercise_records where id=?",
                        RECORD_MAPPER,
                        recordId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("练习记录不存在");
        return rows.getFirst();
    }

    public List<ExerciseRecord> listRecords(
            UUID studentId, String startDate, String endDate, int limit, int offset) {
        StringBuilder where = new StringBuilder(" where student_id=?");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        if (startDate != null && !startDate.isBlank()) {
            where.append(" and created_at >= ?::date");
            args.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            where.append(" and created_at < (?::date + interval '1 day')");
            args.add(endDate);
        }
        args.add(limit);
        args.add(offset);
        return jdbc.query(
                "select id,student_id,subject,total_questions,correct_count,total_score,time_spent,created_at from exercise_records"
                        + where
                        + " order by created_at desc limit ? offset ?",
                RECORD_MAPPER,
                args.toArray());
    }

    public long countRecords(UUID studentId, String startDate, String endDate) {
        StringBuilder where = new StringBuilder(" where student_id=?");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        if (startDate != null && !startDate.isBlank()) {
            where.append(" and created_at >= ?::date");
            args.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            where.append(" and created_at < (?::date + interval '1 day')");
            args.add(endDate);
        }
        Number val =
                jdbc.queryForObject(
                        "select count(*) from exercise_records" + where,
                        Number.class,
                        args.toArray());
        return val == null ? 0L : val.longValue();
    }

    public void createItem(
            UUID recordId,
            UUID questionId,
            String userAnswer,
            String correctAnswer,
            boolean isCorrect,
            int score,
            String analysis,
            String teacherSuggestion) {
        jdbc.update(
                """
                insert into exercise_record_items(id,record_id,question_id,user_answer,correct_answer,is_correct,score,analysis,teacher_suggestion)
                values (?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                recordId,
                questionId,
                userAnswer,
                correctAnswer,
                isCorrect,
                score,
                analysis,
                teacherSuggestion);
    }

    public List<ExerciseRecordItem> listAnalysisItems(UUID recordId, UUID studentId) {
        return jdbc.query(
                """
                select
                  i.id, i.record_id, i.question_id, i.user_answer, i.correct_answer, i.is_correct, i.score,
                  coalesce(i.analysis, q.analysis) as analysis,
                  q.knowledge_points as knowledge_points,
                  coalesce(
                    i.teacher_suggestion,
                    (select s.suggestion from teacher_suggestions s
                     where s.student_id=?
                       and (s.question_id=i.question_id
                            or (s.knowledge_point is not null and q.knowledge_points is not null
                                and jsonb_exists(q.knowledge_points, s.knowledge_point)))
                     order by s.created_at desc limit 1)
                  ) as teacher_suggestion,
                  i.created_at,
                  q.content as content
                from exercise_record_items i
                join questions q on q.id=i.question_id
                where i.record_id=?
                order by i.created_at asc
                """,
                (rs, rn) ->
                        new ExerciseRecordItem(
                                (UUID) rs.getObject("id"),
                                (UUID) rs.getObject("record_id"),
                                (UUID) rs.getObject("question_id"),
                                rs.getString("user_answer"),
                                rs.getString("correct_answer"),
                                rs.getBoolean("is_correct"),
                                rs.getInt("score"),
                                rs.getString("analysis"),
                                rs.getString("teacher_suggestion"),
                                ApiDataMapper.toInstant(rs.getTimestamp("created_at"))),
                studentId,
                recordId);
    }
}
