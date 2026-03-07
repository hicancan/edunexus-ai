package com.edunexus.api.service;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.DependencyException;
import com.edunexus.api.common.ErrorCode;
import com.edunexus.api.domain.ChatMessage;
import com.edunexus.api.domain.ChatSession;
import com.edunexus.api.repository.ChatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private final ChatRepository chatRepo;
    private final AiClient aiClient;
    private final GovernanceService governance;
    private final VoMapper voMapper;
    private final ObjectMapper objectMapper;
    private final TaskExecutor chatStreamExecutor;

    public ChatService(
            ChatRepository chatRepo,
            AiClient aiClient,
            GovernanceService governance,
            VoMapper voMapper,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Qualifier("chatStreamExecutor") TaskExecutor chatStreamExecutor) {
        this.chatRepo = chatRepo;
        this.aiClient = aiClient;
        this.governance = governance;
        this.voMapper = voMapper;
        this.objectMapper = objectMapper;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    public ChatSession createSession(UUID studentId) {
        UUID sessionId = chatRepo.createSession(studentId);
        return chatRepo.findSession(sessionId);
    }

    public List<ChatSession> listSessions(UUID studentId, int page, int size) {
        int offset = (page - 1) * size;
        return chatRepo.listSessions(studentId, size, offset);
    }

    public long countSessions(UUID studentId) {
        return chatRepo.countSessions(studentId);
    }

    public Map<String, Object> getSessionDetail(UUID sessionId) {
        ChatSession session = chatRepo.findSession(sessionId);
        List<ChatMessage> messages = chatRepo.listMessages(sessionId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", session.id().toString());
        detail.put("title", session.title());
        detail.put("createdAt", ApiDataMapper.asIsoTime(session.createdAt()));
        detail.put("messages", messages.stream().map(voMapper::toChatMessageVo).toList());
        return detail;
    }

    public void deleteSession(UUID sessionId) {
        chatRepo.deleteSession(sessionId);
    }

    public ChatSession ensureSessionOwner(UUID sessionId, UUID studentId) {
        ChatSession session = chatRepo.findSession(sessionId);
        if (session.isDeleted())
            throw new com.edunexus.api.common.ResourceNotFoundException("资源不存在");
        if (!studentId.equals(session.studentId())) throw new SecurityException("非资源归属者");
        return session;
    }

    public Map<String, Object> sendMessage(
            UUID sessionId, UUID studentId, String message, String traceId) {
        UUID userMessageId = chatRepo.createUserMessage(sessionId, message);
        Map<String, Object> chatBody = buildChatBody(sessionId, studentId, message, traceId, false);

        Map<String, Object> aiResult = aiClient.chat(chatBody);
        String answer = ApiDataMapper.asString(aiResult.get("answer"));
        if (answer == null || answer.isBlank()) {
            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 返回空响应");
        }
        List<Map<String, Object>> citations =
                ApiDataMapper.parseObjectList(aiResult.get("citations"), objectMapper);
        int tokenUsage = parseTokenUsage(aiResult.get("tokenUsage"));
        return persistAssistantReply(sessionId, userMessageId, answer, citations, tokenUsage);
    }

    public SseEmitter streamMessage(
            UUID sessionId, UUID studentId, String message, String traceId) {
        UUID userMessageId = chatRepo.createUserMessage(sessionId, message);
        Map<String, Object> chatBody = buildChatBody(sessionId, studentId, message, traceId, true);

        SseEmitter emitter = new SseEmitter(70_000L);
        chatStreamExecutor.execute(
                () -> {
                    StringBuilder answerBuilder = new StringBuilder();
                    List<Map<String, Object>> citations = new ArrayList<>();
                    try {
                        sendSseEvent(emitter, Map.of("stage", "generating"));
                        aiClient.chatStream(
                                chatBody,
                                chunk -> {
                                    List<Map<String, Object>> frameCitations = chunk.citations();
                                    if (frameCitations != null && !frameCitations.isEmpty()) {
                                        citations.clear();
                                        citations.addAll(frameCitations);
                                    }
                                    String delta = chunk.delta() == null ? "" : chunk.delta();
                                    if (!delta.isBlank()
                                            || (frameCitations != null
                                                    && !frameCitations.isEmpty())) {
                                        answerBuilder.append(delta);
                                        sendSseEvent(
                                                emitter,
                                                Map.of(
                                                        "delta",
                                                        delta,
                                                        "citations",
                                                        frameCitations == null
                                                                ? List.of()
                                                                : frameCitations));
                                    }
                                });

                        String answer = answerBuilder.toString().trim();
                        if (answer.isBlank())
                            throw new DependencyException(ErrorCode.AI_OUTPUT_INVALID, "AI 返回空响应");

                        Map<String, Object> data =
                                persistAssistantReply(
                                        sessionId, userMessageId, answer, citations, 0);
                        sendSseEvent(
                                emitter, Map.of("done", true, "data", data, "traceId", traceId));
                        sendSseEvent(emitter, "[DONE]");
                        emitter.complete();
                    } catch (Exception ex) {
                        String errMsg =
                                ex.getMessage() == null || ex.getMessage().isBlank()
                                        ? "发送消息失败"
                                        : ex.getMessage();
                        try {
                            sendSseEvent(
                                    emitter,
                                    Map.of(
                                            "error", errMsg, "traceId", traceId, "message",
                                            message));
                        } catch (Exception ignored) {
                        }
                        emitter.completeWithError(ex);
                    }
                });
        return emitter;
    }

    private Map<String, Object> buildChatBody(
            UUID sessionId, UUID studentId, String message, String traceId, boolean stream) {
        List<ChatMessage> historyMessages = chatRepo.listRecentHistory(sessionId, 30);
        List<Map<String, Object>> history =
                historyMessages.stream()
                        .map(
                                m -> {
                                    Map<String, Object> item = new LinkedHashMap<>();
                                    item.put("role", m.role());
                                    item.put("content", m.content());
                                    return item;
                                })
                        .toList();

        String binding = chatRepo.findTeacherBinding(studentId);
        String teacherId = null;
        String classId = null;
        if (binding != null) {
            String[] parts = binding.split(":", 2);
            teacherId = parts[0];
            classId = parts.length > 1 && !parts[1].isBlank() ? parts[1] : null;
        }

        Map<String, Object> teacherScope = new LinkedHashMap<>();
        teacherScope.put("teacherId", teacherId);
        teacherScope.put("classId", classId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traceId", traceId);
        body.put("sessionId", sessionId.toString());
        body.put("studentId", studentId.toString());
        body.put("message", message);
        body.put("stream", stream);
        body.put("context", Map.of("history", history));
        body.put("teacherScope", teacherScope);
        return body;
    }

    private Map<String, Object> persistAssistantReply(
            UUID sessionId,
            UUID userMessageId,
            String answer,
            List<Map<String, Object>> citations,
            int tokenUsage) {
        String citationsJson;
        try {
            citationsJson = objectMapper.writeValueAsString(citations);
        } catch (Exception ex) {
            citationsJson = "[]";
        }
        UUID assistantMessageId =
                chatRepo.createAssistantMessage(sessionId, answer, citationsJson, tokenUsage);
        chatRepo.touchSession(sessionId);
        chatRepo.updateSessionTitleIfNeeded(sessionId);

        ChatMessage userMessage = chatRepo.findMessage(userMessageId);
        ChatMessage assistantMessage = chatRepo.findMessage(assistantMessageId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userMessage", voMapper.toChatMessageVo(userMessage));
        data.put("assistantMessage", voMapper.toChatMessageVo(assistantMessage));
        return data;
    }

    private int parseTokenUsage(Object tokenUsagePayload) {
        if (tokenUsagePayload == null) return 0;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map =
                    objectMapper.convertValue(
                            tokenUsagePayload,
                            new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return ApiDataMapper.asInt(map.get("prompt"))
                    + ApiDataMapper.asInt(map.get("completion"));
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }

    private void sendSseEvent(SseEmitter emitter, Object payload) {
        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
