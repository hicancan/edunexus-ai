package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final RowMapper<User> ROW_MAPPER =
            (rs, rn) ->
                    new User(
                            (UUID) rs.getObject("id"),
                            rs.getString("username"),
                            rs.getString("role"),
                            rs.getString("status"),
                            rs.getString("email"),
                            rs.getString("phone"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")),
                            ApiDataMapper.toInstant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsByUsername(String username) {
        var rows =
                jdbc.queryForList(
                        "select 1 from users where username=? and deleted_at is null", username);
        return !rows.isEmpty();
    }

    public User findById(UUID id) {
        List<User> rows =
                jdbc.query(
                        "select id,username,role,status,email,phone,created_at,updated_at from users where id=?",
                        ROW_MAPPER,
                        id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("用户不存在");
        return rows.getFirst();
    }

    public UUID create(
            String username, String passwordHash, String email, String phone, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into users(id,username,password_hash,email,phone,role,status) values (?,?,?,?,?,?,'ACTIVE')",
                id,
                username,
                passwordHash,
                email,
                phone,
                role);
        return id;
    }

    public void patchRoleStatus(UUID id, String role, String status) {
        jdbc.update(
                "update users set role=?,status=?,updated_at=now() where id=?", role, status, id);
    }

    public List<User> list(String role, String status, int limit, int offset) {
        StringBuilder where = new StringBuilder(" where deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            where.append(" and role=?");
            args.add(role);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status=?");
            args.add(status);
        }
        args.add(limit);
        args.add(offset);
        return jdbc.query(
                "select id,username,role,status,email,phone,created_at,updated_at from users"
                        + where
                        + " order by created_at desc limit ? offset ?",
                ROW_MAPPER,
                args.toArray());
    }

    public long count(String role, String status) {
        StringBuilder where = new StringBuilder(" where deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            where.append(" and role=?");
            args.add(role);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status=?");
            args.add(status);
        }
        Number val =
                jdbc.queryForObject(
                        "select count(*) from users" + where, Number.class, args.toArray());
        return val == null ? 0L : val.longValue();
    }
}
