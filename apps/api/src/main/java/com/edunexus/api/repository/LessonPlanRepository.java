package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.LessonPlan;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LessonPlanRepository {

    private static final RowMapper<LessonPlan> ROW_MAPPER =
            (rs, rn) ->
                    new LessonPlan(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("teacher_id"),
                            rs.getString("topic"),
                            rs.getString("grade_level"),
                            rs.getInt("duration_mins"),
                            rs.getString("content_md"),
                            rs.getBoolean("is_shared"),
                            rs.getString("share_token"),
                            ApiDataMapper.toInstant(rs.getTimestamp("shared_at")),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")),
                            ApiDataMapper.toInstant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public LessonPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(
            UUID teacherId, String topic, String gradeLevel, int durationMins, String contentMd) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into lesson_plans(id,teacher_id,topic,grade_level,duration_mins,content_md) values (?,?,?,?,?,?)",
                id,
                teacherId,
                topic,
                gradeLevel,
                durationMins,
                contentMd);
        return id;
    }

    public LessonPlan findById(UUID id) {
        List<LessonPlan> rows =
                jdbc.query(
                        "select id,teacher_id,topic,grade_level,duration_mins,content_md,is_shared,share_token,shared_at,created_at,updated_at from lesson_plans where id=? and deleted_at is null",
                        ROW_MAPPER,
                        id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("教案不存在");
        return rows.getFirst();
    }

    public LessonPlan findByShareToken(String shareToken) {
        List<LessonPlan> rows =
                jdbc.query(
                        "select id,teacher_id,topic,grade_level,duration_mins,content_md,is_shared,share_token,shared_at,created_at,updated_at from lesson_plans where share_token=? and is_shared=true and deleted_at is null",
                        ROW_MAPPER,
                        shareToken);
        if (rows.isEmpty()) throw new ResourceNotFoundException("共享教案不存在");
        return rows.getFirst();
    }

    public List<LessonPlan> list(UUID teacherId, int limit, int offset) {
        return jdbc.query(
                "select id,teacher_id,topic,grade_level,duration_mins,content_md,is_shared,share_token,shared_at,created_at,updated_at from lesson_plans where teacher_id=? and deleted_at is null order by updated_at desc limit ? offset ?",
                ROW_MAPPER,
                teacherId,
                limit,
                offset);
    }

    public long count(UUID teacherId) {
        Number val =
                jdbc.queryForObject(
                        "select count(*) from lesson_plans where teacher_id=? and deleted_at is null",
                        Number.class,
                        teacherId);
        return val == null ? 0L : val.longValue();
    }

    public void update(UUID id, String contentMd) {
        jdbc.update(
                "update lesson_plans set content_md=?,updated_at=now() where id=?", contentMd, id);
    }

    public void softDelete(UUID id) {
        jdbc.update("update lesson_plans set deleted_at=now(),updated_at=now() where id=?", id);
    }

    public String enableSharing(UUID id) {
        List<String> tokens =
                jdbc.queryForList(
                        "select share_token from lesson_plans where id=?", String.class, id);
        String shareToken =
                (tokens.isEmpty() || tokens.getFirst() == null || tokens.getFirst().isBlank())
                        ? UUID.randomUUID().toString().replace("-", "").substring(0, 24)
                        : tokens.getFirst();
        jdbc.update(
                "update lesson_plans set is_shared=true,share_token=?,shared_at=coalesce(shared_at,now()),updated_at=now() where id=?",
                shareToken,
                id);
        return shareToken;
    }

    public void ensureOwner(UUID planId, UUID teacherId) {
        List<UUID> rows =
                jdbc.query(
                        "select teacher_id from lesson_plans where id=? and deleted_at is null",
                        (rs, rn) -> (UUID) rs.getObject("teacher_id"),
                        planId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("教案不存在");
        if (!teacherId.equals(rows.getFirst())) throw new SecurityException("非资源归属者");
    }
}
