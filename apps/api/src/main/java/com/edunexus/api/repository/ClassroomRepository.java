package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.domain.Classroom;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClassroomRepository {

    private static final RowMapper<Classroom> ROW_MAPPER =
            (rs, rn) ->
                    new Classroom(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("teacher_id"),
                            rs.getString("name"),
                            rs.getString("status"),
                            ApiDataMapper.asInt(rs.getObject("student_count")));

    private final JdbcTemplate jdbc;

    public ClassroomRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Classroom> listByTeacher(UUID teacherId) {
        return jdbc.query(
                """
                select c.id,c.teacher_id,c.name,c.status,coalesce(count(distinct b.student_id),0) as student_count
                from classrooms c
                left join teacher_student_bindings b
                  on b.classroom_id=c.id and b.status='ACTIVE' and (b.revoked_at is null or b.revoked_at > now())
                where c.teacher_id=? and c.status='ACTIVE'
                group by c.id,c.teacher_id,c.name,c.status
                order by c.name asc
                """,
                ROW_MAPPER,
                teacherId);
    }

    public Classroom ensureOwner(UUID classroomId, UUID teacherId) {
        List<Classroom> rows =
                jdbc.query(
                        "select id,teacher_id,name,status,0 as student_count from classrooms where id=? and teacher_id=? and status='ACTIVE'",
                        ROW_MAPPER,
                        classroomId,
                        teacherId);
        if (rows.isEmpty()) throw new SecurityException("无权限访问该班级");
        return rows.getFirst();
    }
}
