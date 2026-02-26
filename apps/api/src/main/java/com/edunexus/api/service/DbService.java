package com.edunexus.api.service;

import com.edunexus.api.common.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DbService {
    private final JdbcTemplate jdbc;

    public DbService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID newId() {
        return UUID.randomUUID();
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = list(sql, args);
        if (rows.isEmpty()) throw new ResourceNotFoundException("资源不存在");
        return rows.getFirst();
    }

    public boolean exists(String sql, Object... args) {
        SqlRowSet rs = jdbc.queryForRowSet(sql, args);
        return rs.next();
    }
}
