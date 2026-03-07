package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.domain.WrongBookEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WrongBookRepository {

    private static final RowMapper<WrongBookEntry> ROW_MAPPER =
            (rs, rn) ->
                    new WrongBookEntry(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("student_id"),
                            (UUID) rs.getObject("question_id"),
                            rs.getInt("wrong_count"),
                            ApiDataMapper.toInstant(rs.getTimestamp("last_wrong_time")),
                            rs.getString("status"),
                            ApiDataMapper.toInstant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public WrongBookRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(UUID studentId, UUID questionId) {
        var rows =
                jdbc.queryForList(
                        "select 1 from wrong_book where student_id=? and question_id=?",
                        studentId,
                        questionId);
        if (!rows.isEmpty()) {
            jdbc.update(
                    "update wrong_book set wrong_count=wrong_count+1,last_wrong_time=now(),status='ACTIVE',updated_at=now() where student_id=? and question_id=?",
                    studentId,
                    questionId);
        } else {
            jdbc.update(
                    "insert into wrong_book(id,student_id,question_id,wrong_count,status) values (?,?,?,1,'ACTIVE')",
                    UUID.randomUUID(),
                    studentId,
                    questionId);
        }
    }

    public int markMastered(UUID studentId, UUID questionId) {
        return jdbc.update(
                "update wrong_book set status='MASTERED',updated_at=now() where student_id=? and question_id=?",
                studentId,
                questionId);
    }

    public List<WrongBookEntry> list(
            UUID studentId, String status, String subject, int limit, int offset) {
        StringBuilder where = new StringBuilder(" where w.student_id=? and w.status=?");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        args.add(status);
        if (subject != null && !subject.isBlank()) {
            where.append(" and q.subject=?");
            args.add(subject);
        }
        args.add(limit);
        args.add(offset);
        // We return WrongBookEntry but callers need question data, so we embed it in the RowMapper
        // The query uses JOIN to questions for subject filter only; question details come from
        // QuestionRepository
        return jdbc.query(
                "select w.id,w.student_id,w.question_id,w.wrong_count,w.last_wrong_time,w.status,w.updated_at from wrong_book w join questions q on q.id=w.question_id"
                        + where
                        + " order by w.last_wrong_time desc limit ? offset ?",
                ROW_MAPPER,
                args.toArray());
    }

    public long count(UUID studentId, String status, String subject) {
        StringBuilder where = new StringBuilder(" where w.student_id=? and w.status=?");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        args.add(status);
        if (subject != null && !subject.isBlank()) {
            where.append(" and q.subject=?");
            args.add(subject);
        }
        Number val =
                jdbc.queryForObject(
                        "select count(*) from wrong_book w join questions q on q.id=w.question_id"
                                + where,
                        Number.class,
                        args.toArray());
        return val == null ? 0L : val.longValue();
    }

    public long countActive(UUID studentId) {
        Number val =
                jdbc.queryForObject(
                        "select coalesce(sum(wrong_count),0) from wrong_book where student_id=? and status='ACTIVE'",
                        Number.class,
                        studentId);
        return val == null ? 0L : val.longValue();
    }
}
