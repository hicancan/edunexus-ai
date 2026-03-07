package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {

    private static final RowMapper<AuditLog> ROW_MAPPER =
            (rs, rn) ->
                    new AuditLog(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("actor_id"),
                            rs.getString("actor_role"),
                            rs.getString("action"),
                            rs.getString("resource_type"),
                            rs.getString("resource_id"),
                            rs.getString("detail"),
                            rs.getString("ip"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AuditLog> list(int limit, int offset) {
        return jdbc.query(
                "select id,actor_id,actor_role,action,resource_type,resource_id,detail,ip,created_at from audit_logs order by created_at desc limit ? offset ?",
                ROW_MAPPER,
                limit,
                offset);
    }

    public long count() {
        Number val = jdbc.queryForObject("select count(*) from audit_logs", Number.class);
        return val == null ? 0L : val.longValue();
    }
}
