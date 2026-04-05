package com.edunexus.api.service;

import com.edunexus.api.domain.TeacherSuggestion;
import com.edunexus.api.repository.SuggestionRepository;
import com.edunexus.api.repository.TeacherStudentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SuggestionService {
    public record SaveResult(TeacherSuggestion suggestion, boolean created) {}

    public record BulkResult(
            String knowledgePoint,
            int affectedCount,
            int createdCount,
            int updatedCount,
            List<String> studentIds) {}

    private final SuggestionRepository suggestionRepo;
    private final TeacherStudentRepository teacherStudentRepo;
    private final JdbcTemplate jdbc;

    public SuggestionService(
            SuggestionRepository suggestionRepo,
            TeacherStudentRepository teacherStudentRepo,
            JdbcTemplate jdbc) {
        this.suggestionRepo = suggestionRepo;
        this.teacherStudentRepo = teacherStudentRepo;
        this.jdbc = jdbc;
    }

    public TeacherSuggestion create(
            UUID teacherId,
            UUID studentId,
            UUID questionId,
            String knowledgePoint,
            String suggestion) {
        return save(teacherId, studentId, questionId, knowledgePoint, suggestion).suggestion();
    }

    public SaveResult save(
            UUID teacherId,
            UUID studentId,
            UUID questionId,
            String knowledgePoint,
            String suggestion) {
        teacherStudentRepo.ensureLinked(teacherId, studentId);
        var result =
                suggestionRepo.saveOrUpdate(
                        teacherId, studentId, questionId, knowledgePoint, suggestion);
        return new SaveResult(result.suggestion(), result.created());
    }

    public BulkResult createBulk(UUID teacherId, String knowledgePoint, String suggestion) {
        List<Map<String, Object>> students =
                jdbc.queryForList(
                        """
                select distinct b.student_id
                from teacher_student_bindings b
                join wrong_book w on w.student_id=b.student_id and w.status='ACTIVE'
                join questions q on q.id=w.question_id
                where b.teacher_id=? and b.status='ACTIVE'
                  and (b.revoked_at is null or b.revoked_at > now())
                  and q.knowledge_points is not null
                  and jsonb_exists(q.knowledge_points,?)
                """,
                        teacherId,
                        knowledgePoint);

        if (students.isEmpty()) {
            throw new IllegalArgumentException("未检索到需要干预的学生");
        }

        List<String> studentIds = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        for (Map<String, Object> row : students) {
            UUID studentId = (UUID) row.get("student_id");
            studentIds.add(studentId.toString());
            var result = save(teacherId, studentId, null, knowledgePoint, suggestion);
            if (result.created()) {
                createdCount++;
            } else {
                updatedCount++;
            }
        }
        return new BulkResult(
                knowledgePoint, studentIds.size(), createdCount, updatedCount, studentIds);
    }
}
