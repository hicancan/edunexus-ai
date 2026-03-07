package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.TeacherSuggestion;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SuggestionRepository {

    private static final RowMapper<TeacherSuggestion> ROW_MAPPER =
            (rs, rn) ->
                    new TeacherSuggestion(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("teacher_id"),
                            (UUID) rs.getObject("student_id"),
                            (UUID) rs.getObject("question_id"),
                            rs.getString("knowledge_point"),
                            rs.getString("suggestion"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public SuggestionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(
            UUID teacherId,
            UUID studentId,
            UUID questionId,
            String knowledgePoint,
            String suggestion) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into teacher_suggestions(id,teacher_id,student_id,question_id,knowledge_point,suggestion) values (?,?,?,?,?,?)",
                id,
                teacherId,
                studentId,
                questionId,
                knowledgePoint,
                suggestion);
        return id;
    }

    public TeacherSuggestion findById(UUID id) {
        List<TeacherSuggestion> rows =
                jdbc.query(
                        "select id,teacher_id,student_id,question_id,knowledge_point,suggestion,created_at from teacher_suggestions where id=?",
                        ROW_MAPPER,
                        id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("建议不存在");
        return rows.getFirst();
    }

    public String fetchByStudentAndQuestion(UUID studentId, UUID questionId) {
        List<String> rows =
                jdbc.queryForList(
                        "select suggestion from teacher_suggestions where student_id=? and question_id=? order by created_at desc limit 1",
                        String.class,
                        studentId,
                        questionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<TeacherSuggestion> listByStudent(UUID studentId, int limit) {
        return jdbc.query(
                "select id,teacher_id,student_id,question_id,knowledge_point,suggestion,created_at from teacher_suggestions where student_id=? order by created_at desc limit ?",
                ROW_MAPPER,
                studentId,
                limit);
    }
}
