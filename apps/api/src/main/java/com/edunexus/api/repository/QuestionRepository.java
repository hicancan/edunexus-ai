package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.Question;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository {

    private static final RowMapper<Question> ROW_MAPPER =
            (rs, rn) ->
                    new Question(
                            (UUID) rs.getObject("id"),
                            rs.getString("subject"),
                            rs.getString("question_type"),
                            rs.getString("difficulty"),
                            rs.getString("content"),
                            rs.getString("options"),
                            rs.getString("correct_answer"),
                            rs.getString("analysis"),
                            rs.getString("knowledge_points"),
                            rs.getInt("score"),
                            rs.getString("source"),
                            (UUID) rs.getObject("ai_session_id"),
                            (UUID) rs.getObject("created_by"),
                            rs.getBoolean("is_active"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public QuestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Question findById(UUID id) {
        List<Question> rows =
                jdbc.query(
                        "select id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,ai_session_id,created_by,is_active,created_at from questions where id=? and is_active=true",
                        ROW_MAPPER,
                        id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("题目不存在");
        return rows.getFirst();
    }

    public Question findByIdInSession(UUID id, UUID sessionId) {
        List<Question> rows =
                jdbc.query(
                        "select id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,ai_session_id,created_by,is_active,created_at from questions where id=? and ai_session_id=? and is_active=true",
                        ROW_MAPPER,
                        id,
                        sessionId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("题目不存在");
        return rows.getFirst();
    }

    public List<Question> list(String subject, String difficulty, int limit, int offset) {
        StringBuilder where = new StringBuilder(" where is_active=true");
        List<Object> args = new ArrayList<>();
        if (subject != null && !subject.isBlank()) {
            where.append(" and subject=?");
            args.add(subject);
        }
        if (difficulty != null && !difficulty.isBlank()) {
            where.append(" and difficulty=?");
            args.add(difficulty);
        }
        args.add(limit);
        args.add(offset);
        return jdbc.query(
                "select id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,ai_session_id,created_by,is_active,created_at from questions"
                        + where
                        + " order by created_at desc limit ? offset ?",
                ROW_MAPPER,
                args.toArray());
    }

    public long count(String subject, String difficulty) {
        StringBuilder where = new StringBuilder(" where is_active=true");
        List<Object> args = new ArrayList<>();
        if (subject != null && !subject.isBlank()) {
            where.append(" and subject=?");
            args.add(subject);
        }
        if (difficulty != null && !difficulty.isBlank()) {
            where.append(" and difficulty=?");
            args.add(difficulty);
        }
        Number val =
                jdbc.queryForObject(
                        "select count(*) from questions" + where, Number.class, args.toArray());
        return val == null ? 0L : val.longValue();
    }

    public UUID createAiGenerated(
            String subject,
            String questionType,
            String difficulty,
            String content,
            String optionsJson,
            String correctAnswer,
            String analysis,
            String knowledgePointsJson,
            UUID sessionId,
            UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into questions(id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,ai_session_id,created_by)
                values (?,?,?,?,?,?::jsonb,?,?,?::jsonb,5,'AI_GENERATED',?,?)
                """,
                id,
                subject,
                questionType,
                difficulty,
                content,
                optionsJson,
                correctAnswer,
                analysis,
                knowledgePointsJson,
                sessionId,
                createdBy);
        return id;
    }
}
