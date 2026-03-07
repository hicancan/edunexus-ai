package com.edunexus.api.repository;

import com.edunexus.api.domain.TeacherStudent;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TeacherStudentRepository {

    private static final RowMapper<TeacherStudent> ROW_MAPPER =
            (rs, rn) ->
                    new TeacherStudent(
                            (UUID) rs.getObject("id"),
                            rs.getString("username"),
                            (UUID) rs.getObject("classroom_id"),
                            rs.getString("classroom_name"));

    private final JdbcTemplate jdbc;

    public TeacherStudentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TeacherStudent> listByTeacher(UUID teacherId) {
        return jdbc.query(
                """
                select distinct u.id,u.username,b.classroom_id,c.name as classroom_name
                from teacher_student_bindings b
                join users u on u.id=b.student_id
                left join classrooms c on c.id=b.classroom_id
                where b.teacher_id=? and b.status='ACTIVE' and u.role='STUDENT' and u.deleted_at is null
                  and (b.revoked_at is null or b.revoked_at > now())
                order by u.username asc
                """,
                ROW_MAPPER,
                teacherId);
    }

    public boolean isLinked(UUID teacherId, UUID studentId) {
        var rows =
                jdbc.queryForList(
                        """
                select 1 from teacher_student_bindings
                where teacher_id=? and student_id=? and status='ACTIVE'
                  and (revoked_at is null or revoked_at > now())
                """,
                        teacherId,
                        studentId);
        return !rows.isEmpty();
    }

    public void ensureLinked(UUID teacherId, UUID studentId) {
        if (!isLinked(teacherId, studentId)) {
            throw new SecurityException("无权限访问该学生");
        }
    }
}
