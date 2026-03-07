package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.domain.AdminResource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AdminResourceRepository {

    private static final String RESOURCE_CTE =
            """
            with resources as (
              select lp.id as resource_id,'LESSON_PLAN'::varchar as resource_type,
                     lp.topic as title,lp.teacher_id as creator_id,u.username as creator_username,lp.created_at
              from lesson_plans lp join users u on u.id=lp.teacher_id where lp.deleted_at is null
              union all
              select q.id,'QUESTION'::varchar,left(q.content,120),q.created_by,u.username,q.created_at
              from questions q left join users u on u.id=q.created_by where q.is_active=true
              union all
              select d.id,'DOCUMENT'::varchar,d.filename,d.teacher_id,u.username,d.created_at
              from documents d join users u on u.id=d.teacher_id where d.deleted_at is null
            )
            """;

    private static final RowMapper<AdminResource> ROW_MAPPER =
            (rs, rn) ->
                    new AdminResource(
                            (UUID) rs.getObject("resource_id"),
                            rs.getString("resource_type"),
                            rs.getString("title"),
                            (UUID) rs.getObject("creator_id"),
                            rs.getString("creator_username"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public AdminResourceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminResource> list(String resourceType, int limit, int offset) {
        List<Object> args = new ArrayList<>();
        String filter = "";
        if (resourceType != null && !resourceType.isBlank()) {
            filter = " where resource_type=?";
            args.add(resourceType);
        }
        args.add(limit);
        args.add(offset);
        return jdbc.query(
                RESOURCE_CTE
                        + " select resource_id,resource_type,title,creator_id,creator_username,created_at from resources"
                        + filter
                        + " order by created_at desc limit ? offset ?",
                ROW_MAPPER,
                args.toArray());
    }

    public long count(String resourceType) {
        List<Object> args = new ArrayList<>();
        String filter = "";
        if (resourceType != null && !resourceType.isBlank()) {
            filter = " where resource_type=?";
            args.add(resourceType);
        }
        Number val =
                jdbc.queryForObject(
                        RESOURCE_CTE + " select count(*) from resources" + filter,
                        Number.class,
                        args.toArray());
        return val == null ? 0L : val.longValue();
    }
}
