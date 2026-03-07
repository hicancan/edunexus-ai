package com.edunexus.api.service;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.domain.TeacherStudent;
import com.edunexus.api.repository.TeacherStudentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final TeacherStudentRepository teacherStudentRepo;
    private final JdbcTemplate jdbc;

    public AnalyticsService(TeacherStudentRepository teacherStudentRepo, JdbcTemplate jdbc) {
        this.teacherStudentRepo = teacherStudentRepo;
        this.jdbc = jdbc;
    }

    public List<TeacherStudent> listStudents(UUID teacherId) {
        return teacherStudentRepo.listByTeacher(teacherId);
    }

    public void ensureStudentLinked(UUID teacherId, UUID studentId) {
        teacherStudentRepo.ensureLinked(teacherId, studentId);
    }

    public Map<String, Object> getStudentAnalytics(UUID studentId) {
        String username = getStudentUsername(studentId);
        long totalExercises =
                countOf("select count(*) from exercise_records where student_id=?", studentId);
        long totalQuestions =
                countOf(
                        "select coalesce(sum(total_questions),0) from exercise_records where student_id=?",
                        studentId);
        long correctCount =
                countOf(
                        "select coalesce(sum(correct_count),0) from exercise_records where student_id=?",
                        studentId);
        double averageScore =
                ApiDataMapper.asDouble(
                        jdbc
                                .queryForList(
                                        "select coalesce(avg(total_score),0) as avg_score from exercise_records where student_id=?",
                                        studentId)
                                .stream()
                                .findFirst()
                                .map(r -> r.get("avg_score"))
                                .orElse(0));
        long wrongBookCount =
                countOf(
                        "select count(*) from wrong_book where student_id=? and status='ACTIVE'",
                        studentId);

        List<Map<String, Object>> weakRows =
                jdbc.queryForList(
                        """
                select kp.knowledge_point,count(*) as wrong_count
                from wrong_book w
                join questions q on q.id=w.question_id
                join lateral jsonb_array_elements_text(coalesce(q.knowledge_points,'[]'::jsonb)) as kp(knowledge_point) on true
                where w.student_id=? and w.status='ACTIVE'
                group by kp.knowledge_point
                order by count(*) desc
                limit 5
                """,
                        studentId);

        List<Map<String, Object>> topWeakPoints =
                weakRows.stream()
                        .map(
                                row -> {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put(
                                            "knowledgePoint",
                                            String.valueOf(row.get("knowledge_point")));
                                    item.put(
                                            "wrongCount",
                                            ApiDataMapper.asInt(row.get("wrong_count")));
                                    return item;
                                })
                        .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentId", studentId.toString());
        data.put("username", username);
        data.put("totalExercises", totalExercises);
        data.put("totalQuestions", totalQuestions);
        data.put("correctCount", correctCount);
        data.put("averageScore", averageScore);
        data.put("wrongBookCount", wrongBookCount);
        data.put("topWeakPoints", topWeakPoints);
        return data;
    }

    public Map<String, Object> getStudentAttribution(UUID studentId) {
        List<Map<String, Object>> topRows =
                jdbc.queryForList(
                        """
                select kp.knowledge_point,coalesce(sum(w.wrong_count),0) as wrong_count
                from wrong_book w
                join questions q on q.id=w.question_id
                join lateral jsonb_array_elements_text(coalesce(q.knowledge_points,'[]'::jsonb)) as kp(knowledge_point) on true
                where w.student_id=? and w.status='ACTIVE'
                group by kp.knowledge_point
                order by wrong_count desc, kp.knowledge_point asc
                limit 1
                """,
                        studentId);

        if (topRows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("knowledgePoint", null);
            empty.put("impactCount", 0);
            empty.put("examples", List.of());
            empty.put("summary", null);
            return empty;
        }

        String knowledgePoint = String.valueOf(topRows.getFirst().get("knowledge_point"));
        int impactCount = ApiDataMapper.asInt(topRows.getFirst().get("wrong_count"));

        List<Map<String, Object>> examples =
                jdbc
                        .queryForList(
                                """
                select left(q.content,160) as content,w.wrong_count,w.last_wrong_time
                from wrong_book w join questions q on q.id=w.question_id
                where w.student_id=? and w.status='ACTIVE'
                  and q.knowledge_points is not null
                  and jsonb_exists(q.knowledge_points,?)
                order by w.wrong_count desc, w.last_wrong_time desc
                limit 3
                """,
                                studentId,
                                knowledgePoint)
                        .stream()
                        .map(
                                row -> {
                                    Map<String, Object> out = new LinkedHashMap<>();
                                    out.put("content", ApiDataMapper.asString(row.get("content")));
                                    out.put(
                                            "wrongCount",
                                            ApiDataMapper.asInt(row.get("wrong_count")));
                                    out.put(
                                            "lastWrongTime",
                                            ApiDataMapper.asIsoTime(row.get("last_wrong_time")));
                                    return out;
                                })
                        .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("knowledgePoint", knowledgePoint);
        data.put("impactCount", impactCount);
        data.put("examples", examples);
        data.put("summary", "该学生在「" + knowledgePoint + "」上存在稳定错误模式，建议先复盘概念定义，再进行分层训练。");
        return data;
    }

    public List<Map<String, Object>> getInterventionRecommendations(UUID teacherId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                select kp.knowledge_point,count(distinct b.student_id) as student_count,
                       coalesce(sum(w.wrong_count),0) as total_wrong_count
                from teacher_student_bindings b
                join wrong_book w on w.student_id=b.student_id and w.status='ACTIVE'
                join questions q on q.id=w.question_id
                join lateral jsonb_array_elements_text(coalesce(q.knowledge_points,'[]'::jsonb)) as kp(knowledge_point) on true
                where b.teacher_id=? and b.status='ACTIVE' and (b.revoked_at is null or b.revoked_at > now())
                group by kp.knowledge_point
                order by total_wrong_count desc, student_count desc, kp.knowledge_point asc
                limit 12
                """,
                        teacherId);
        return rows.stream()
                .map(
                        row -> {
                            String kp = String.valueOf(row.get("knowledge_point"));
                            Map<String, Object> out = new LinkedHashMap<>();
                            out.put("knowledgePoint", kp);
                            out.put("studentCount", ApiDataMapper.asInt(row.get("student_count")));
                            out.put(
                                    "totalWrongCount",
                                    ApiDataMapper.asInt(row.get("total_wrong_count")));
                            out.put("suggestionTemplate", "围绕「" + kp + "」补齐概念讲解与分层训练，先纠错再迁移应用。");
                            return out;
                        })
                .toList();
    }

    private String getStudentUsername(UUID studentId) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "select username from users where id=? and role='STUDENT' and deleted_at is null",
                        studentId);
        return rows.isEmpty() ? "" : String.valueOf(rows.getFirst().get("username"));
    }

    private long countOf(String sql, Object... args) {
        Number val = jdbc.queryForObject(sql, Number.class, args);
        return val == null ? 0L : val.longValue();
    }
}
