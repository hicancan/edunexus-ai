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
    public record SaveResult(TeacherSuggestion suggestion, boolean created) {}

    public record DispatchSummary(
            String knowledgePoint, int dispatchedStudentCount, java.time.Instant lastDispatchedAt) {}

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

    public SaveResult saveOrUpdate(
            UUID teacherId,
            UUID studentId,
            UUID questionId,
            String knowledgePoint,
            String suggestion) {
        UUID existingId =
                questionId != null
                        ? findExistingQuestionSuggestionId(teacherId, studentId, questionId)
                        : findExistingKnowledgeSuggestionId(teacherId, studentId, knowledgePoint);
        if (existingId != null) {
            jdbc.update(
                    """
                    update teacher_suggestions
                    set question_id = ?,
                        knowledge_point = ?,
                        suggestion = ?,
                        updated_at = now()
                    where id = ?
                    """,
                    questionId,
                    knowledgePoint,
                    suggestion,
                    existingId);
            return new SaveResult(findById(existingId), false);
        }

        UUID id = create(teacherId, studentId, questionId, knowledgePoint, suggestion);
        return new SaveResult(findById(id), true);
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

    public List<DispatchSummary> listDispatchSummaries(UUID teacherId) {
        return jdbc.query(
                """
                select
                  knowledge_point,
                  count(distinct student_id) as dispatched_student_count,
                  max(updated_at) as last_dispatched_at
                from teacher_suggestions
                where teacher_id = ?
                  and question_id is null
                  and knowledge_point is not null
                  and btrim(knowledge_point) <> ''
                group by knowledge_point
                """,
                (rs, rn) ->
                        new DispatchSummary(
                                rs.getString("knowledge_point"),
                                rs.getInt("dispatched_student_count"),
                                ApiDataMapper.toInstant(rs.getTimestamp("last_dispatched_at"))),
                teacherId);
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
                        "select suggestion from teacher_suggestions where student_id=? and question_id=? order by updated_at desc, created_at desc limit 1",
                        String.class,
                        studentId,
                        questionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<TeacherSuggestion> listByStudent(UUID studentId, int limit) {
        return jdbc.query(
                "select id,teacher_id,student_id,question_id,knowledge_point,suggestion,created_at from teacher_suggestions where student_id=? order by updated_at desc, created_at desc limit ?",
                ROW_MAPPER,
                studentId,
                limit);
    }

    private UUID findExistingQuestionSuggestionId(UUID teacherId, UUID studentId, UUID questionId) {
        List<UUID> rows =
                jdbc.queryForList(
                        """
                        select id
                        from teacher_suggestions
                        where teacher_id = ?
                          and student_id = ?
                          and question_id = ?
                        order by updated_at desc, created_at desc
                        limit 1
                        """,
                        UUID.class,
                        teacherId,
                        studentId,
                        questionId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private UUID findExistingKnowledgeSuggestionId(
            UUID teacherId, UUID studentId, String knowledgePoint) {
        if (knowledgePoint == null || knowledgePoint.isBlank()) {
            return null;
        }
        List<UUID> rows =
                jdbc.queryForList(
                        """
                        select id
                        from teacher_suggestions
                        where teacher_id = ?
                          and student_id = ?
                          and question_id is null
                          and knowledge_point = ?
                        order by updated_at desc, created_at desc
                        limit 1
                        """,
                        UUID.class,
                        teacherId,
                        studentId,
                        knowledgePoint);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
