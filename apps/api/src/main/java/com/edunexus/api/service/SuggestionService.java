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
        teacherStudentRepo.ensureLinked(teacherId, studentId);
        UUID id =
                suggestionRepo.create(teacherId, studentId, questionId, knowledgePoint, suggestion);
        return suggestionRepo.findById(id);
    }

    public record BulkResult(String knowledgePoint, int createdCount, List<String> studentIds) {}

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
        for (Map<String, Object> row : students) {
            UUID studentId = (UUID) row.get("student_id");
            studentIds.add(studentId.toString());
            suggestionRepo.create(teacherId, studentId, null, knowledgePoint, suggestion);
        }
        return new BulkResult(knowledgePoint, studentIds.size(), studentIds);
    }
}
