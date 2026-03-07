package com.edunexus.api.repository;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.ChatMessage;
import com.edunexus.api.domain.ChatSession;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRepository {

    private static final RowMapper<ChatSession> SESSION_MAPPER =
            (rs, rn) ->
                    new ChatSession(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("student_id"),
                            rs.getString("title"),
                            rs.getBoolean("is_deleted"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")),
                            ApiDataMapper.toInstant(rs.getTimestamp("updated_at")));

    private static final RowMapper<ChatMessage> MESSAGE_MAPPER =
            (rs, rn) ->
                    new ChatMessage(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("session_id"),
                            rs.getString("role"),
                            rs.getString("content"),
                            rs.getString("citations"),
                            rs.getInt("token_usage"),
                            ApiDataMapper.toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public ChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID createSession(UUID studentId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into chat_sessions(id,student_id,title) values (?,?,?)",
                id,
                studentId,
                "新建对话");
        return id;
    }

    public ChatSession findSession(UUID sessionId) {
        List<ChatSession> rows =
                jdbc.query(
                        "select id,student_id,title,is_deleted,created_at,updated_at from chat_sessions where id=?",
                        SESSION_MAPPER,
                        sessionId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("会话不存在");
        return rows.getFirst();
    }

    public List<ChatSession> listSessions(UUID studentId, int limit, int offset) {
        return jdbc.query(
                """
                select id,student_id,title,is_deleted,created_at,updated_at
                from chat_sessions
                where student_id=? and is_deleted=false
                order by updated_at desc
                limit ? offset ?
                """,
                SESSION_MAPPER,
                studentId,
                limit,
                offset);
    }

    public long countSessions(UUID studentId) {
        Number val =
                jdbc.queryForObject(
                        "select count(*) from chat_sessions where student_id=? and is_deleted=false",
                        Number.class,
                        studentId);
        return val == null ? 0L : val.longValue();
    }

    public void deleteSession(UUID sessionId) {
        jdbc.update(
                "update chat_sessions set is_deleted=true,deleted_at=now(),updated_at=now() where id=?",
                sessionId);
    }

    public UUID createUserMessage(UUID sessionId, String content) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into chat_messages(id,session_id,role,content,citations,token_usage) values (?,?,'USER',?,null,0)",
                id,
                sessionId,
                content);
        return id;
    }

    public UUID createAssistantMessage(
            UUID sessionId, String content, String citationsJson, int tokenUsage) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into chat_messages(id,session_id,role,content,citations,token_usage) values (?,?,'ASSISTANT',?,?::jsonb,?)",
                id,
                sessionId,
                content,
                citationsJson,
                tokenUsage);
        return id;
    }

    public List<ChatMessage> listMessages(UUID sessionId) {
        return jdbc.query(
                """
                select id,session_id,role,content,citations,token_usage,created_at
                from chat_messages
                where session_id=?
                order by created_at asc
                """,
                MESSAGE_MAPPER,
                sessionId);
    }

    public List<ChatMessage> listRecentHistory(UUID sessionId, int limit) {
        return jdbc.query(
                "select id,session_id,role,content,citations,token_usage,created_at from chat_messages where session_id=? order by created_at asc limit ?",
                MESSAGE_MAPPER,
                sessionId,
                limit);
    }

    public ChatMessage findMessage(UUID messageId) {
        List<ChatMessage> rows =
                jdbc.query(
                        "select id,session_id,role,content,citations,token_usage,created_at from chat_messages where id=?",
                        MESSAGE_MAPPER,
                        messageId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("消息不存在");
        return rows.getFirst();
    }

    public void touchSession(UUID sessionId) {
        jdbc.update("update chat_sessions set updated_at=now() where id=?", sessionId);
    }

    public void updateSessionTitleIfNeeded(UUID sessionId) {
        List<String> rows =
                jdbc.queryForList(
                        "select content from chat_messages where session_id=? and role='USER' order by created_at asc limit 1",
                        String.class,
                        sessionId);
        if (rows.isEmpty()) return;
        String title = rows.getFirst();
        title = title.length() > 20 ? title.substring(0, 20) : title;
        jdbc.update(
                "update chat_sessions set title=?,updated_at=now() where id=? and title='新建对话'",
                title,
                sessionId);
    }

    public String findTeacherBinding(UUID studentId) {
        List<String> rows =
                jdbc.queryForList(
                        """
                select teacher_id || ':' || coalesce(classroom_id::text,'')
                from teacher_student_bindings
                where student_id=? and status='ACTIVE' and (revoked_at is null or revoked_at > now())
                order by coalesce(effective_from,created_at) asc limit 1
                """,
                        String.class,
                        studentId);
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
