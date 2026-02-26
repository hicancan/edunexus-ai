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
        Map<String, Object> row = oneOrNull(sql, args);
        if (row == null) {
            throw new ResourceNotFoundException("资源不存在");
        }
        return row;
    }

    public Map<String, Object> oneOrNull(String sql, Object... args) {
        List<Map<String, Object>> rows = list(sql, args);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.getFirst();
    }

    public boolean exists(String sql, Object... args) {
        SqlRowSet rs = jdbc.queryForRowSet(sql, args);
        return rs.next();
    }

    public long count(String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        if (value == null) {
            return 0L;
        }
        return value.longValue();
    }
}
