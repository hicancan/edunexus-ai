package com.edunexus.api.service;

import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.domain.AdminResource;
import com.edunexus.api.domain.AiQuestionRecordItem;
import com.edunexus.api.domain.AiQuestionSession;
import com.edunexus.api.domain.AuditLog;
import com.edunexus.api.domain.ChatMessage;
import com.edunexus.api.domain.ChatSession;
import com.edunexus.api.domain.Classroom;
import com.edunexus.api.domain.DashboardMetrics;
import com.edunexus.api.domain.Document;
import com.edunexus.api.domain.ExerciseRecord;
import com.edunexus.api.domain.ExerciseRecordItem;
import com.edunexus.api.domain.LessonPlan;
import com.edunexus.api.domain.Question;
import com.edunexus.api.domain.TeacherStudent;
import com.edunexus.api.domain.TeacherSuggestion;
import com.edunexus.api.domain.User;
import com.edunexus.api.domain.WeakPoint;
import com.edunexus.api.domain.WrongBookEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class VoMapper {

    private final ObjectMapper objectMapper;

    public VoMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toUserVo(User u) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.id().toString());
        out.put("username", u.username());
        out.put("role", u.role());
        out.put("status", u.status());
        out.put("email", u.email());
        out.put("phone", u.phone());
        out.put("createdAt", ApiDataMapper.asIsoTime(u.createdAt()));
        out.put("updatedAt", ApiDataMapper.asIsoTime(u.updatedAt()));
        return out;
    }

    public Map<String, Object> toChatSessionVo(ChatSession s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id().toString());
        out.put("studentId", s.studentId().toString());
        out.put("title", s.title());
        out.put("createdAt", ApiDataMapper.asIsoTime(s.createdAt()));
        out.put("updatedAt", ApiDataMapper.asIsoTime(s.updatedAt()));
        return out;
    }

    public Map<String, Object> toChatMessageVo(ChatMessage m) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> citations =
                ApiDataMapper.parseObjectList(m.citationsJson(), objectMapper);
        out.put("id", m.id().toString());
        out.put("role", m.role());
        out.put("content", m.content());
        out.put("citations", citations.isEmpty() ? null : citations);
        out.put("tokenUsage", m.tokenUsage());
        out.put("createdAt", ApiDataMapper.asIsoTime(m.createdAt()));
        return out;
    }

    public Map<String, Object> toQuestionVo(Question q) {
        return toQuestionVo(q, true);
    }

    public Map<String, Object> toQuestionVoForStudent(Question q) {
        return toQuestionVo(q, false);
    }

    private Map<String, Object> toQuestionVo(Question q, boolean includeCorrectAnswer) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", q.id().toString());
        out.put("subject", q.subject());
        out.put("questionType", q.questionType());
        out.put("difficulty", q.difficulty());
        out.put("content", q.content());
        out.put("options", ApiDataMapper.parseNullableStringMap(q.optionsJson(), objectMapper));
        if (includeCorrectAnswer) {
            out.put("correctAnswer", q.correctAnswer());
        }
        out.put("analysis", q.analysis());
        out.put(
                "knowledgePoints",
                ApiDataMapper.parseNullableStringList(q.knowledgePointsJson(), objectMapper));
        out.put("score", q.score());
        out.put("source", q.source());
        out.put("createdAt", ApiDataMapper.asIsoTime(q.createdAt()));
        return out;
    }

    public Map<String, Object> toExerciseRecordVo(ExerciseRecord r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.id().toString());
        out.put("subject", r.subject());
        out.put("totalQuestions", r.totalQuestions());
        out.put("correctCount", r.correctCount());
        out.put("totalScore", r.totalScore());
        out.put("timeSpent", r.timeSpent());
        out.put("createdAt", ApiDataMapper.asIsoTime(r.createdAt()));
        return out;
    }

    public Map<String, Object> toExerciseAnalysisItemVo(
            ExerciseRecordItem i, String content, String knowledgePointsJson) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questionId", i.questionId().toString());
        out.put("content", content);
        out.put("userAnswer", i.userAnswer());
        out.put("correctAnswer", i.correctAnswer());
        out.put("isCorrect", i.isCorrect());
        out.put("analysis", i.analysis());
        out.put(
                "knowledgePoints",
                ApiDataMapper.parseNullableStringList(knowledgePointsJson, objectMapper));
        out.put("teacherSuggestion", i.teacherSuggestion());
        return out;
    }

    public Map<String, Object> toAiQuestionRecordItemVo(
            AiQuestionRecordItem i,
            String content,
            String knowledgePointsJson,
            String teacherSuggestion) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questionId", i.questionId().toString());
        out.put("content", content);
        out.put("userAnswer", i.userAnswer());
        out.put("correctAnswer", i.correctAnswer());
        out.put("isCorrect", i.isCorrect());
        out.put("analysis", i.analysis());
        out.put(
                "knowledgePoints",
                ApiDataMapper.parseNullableStringList(knowledgePointsJson, objectMapper));
        out.put("teacherSuggestion", teacherSuggestion);
        return out;
    }

    public Map<String, Object> toWrongBookEntryVo(WrongBookEntry w, Question q) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", q.id().toString());
        question.put("subject", q.subject());
        question.put("questionType", q.questionType());
        question.put("difficulty", q.difficulty());
        question.put("content", q.content());
        question.put(
                "options", ApiDataMapper.parseNullableStringMap(q.optionsJson(), objectMapper));
        question.put("correctAnswer", q.correctAnswer());
        question.put("analysis", q.analysis());
        question.put(
                "knowledgePoints",
                ApiDataMapper.parseNullableStringList(q.knowledgePointsJson(), objectMapper));
        question.put("score", q.score());
        question.put("source", q.source());
        question.put("createdAt", ApiDataMapper.asIsoTime(q.createdAt()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", w.id().toString());
        out.put("questionId", w.questionId().toString());
        out.put("question", question);
        out.put("wrongCount", w.wrongCount());
        out.put("lastWrongTime", ApiDataMapper.asIsoTime(w.lastWrongTime()));
        out.put("status", w.status());
        return out;
    }

    public Map<String, Object> toAiSessionVo(AiQuestionSession s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id().toString());
        out.put("subject", s.subject());
        out.put("difficulty", s.difficulty());
        out.put("questionCount", s.questionCount());
        out.put("completed", s.completed());
        out.put("correctRate", s.correctRate());
        out.put("score", s.score());
        out.put("recordId", s.recordId() == null ? null : s.recordId().toString());
        out.put("generatedAt", ApiDataMapper.asIsoTime(s.generatedAt()));
        return out;
    }

    public Map<String, Object> toLessonPlanVo(LessonPlan p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", p.id().toString());
        out.put("topic", p.topic());
        out.put("gradeLevel", p.gradeLevel());
        out.put("durationMins", p.durationMins());
        out.put("contentMd", p.contentMd());
        out.put("isShared", p.isShared());
        out.put("shareToken", p.shareToken());
        out.put("sharedAt", ApiDataMapper.asIsoTime(p.sharedAt()));
        out.put("createdAt", ApiDataMapper.asIsoTime(p.createdAt()));
        out.put("updatedAt", ApiDataMapper.asIsoTime(p.updatedAt()));
        return out;
    }

    public Map<String, Object> toDocumentVo(Document d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.id().toString());
        out.put("filename", d.filename());
        out.put("fileType", d.fileType());
        out.put("fileSize", d.fileSize());
        out.put("classId", d.classroomId() == null ? null : d.classroomId().toString());
        out.put("className", d.classroomName());
        out.put("status", d.status());
        out.put("errorMessage", d.errorMessage());
        out.put("createdAt", ApiDataMapper.asIsoTime(d.createdAt()));
        out.put("updatedAt", ApiDataMapper.asIsoTime(d.updatedAt()));
        return out;
    }

    public Map<String, Object> toClassroomVo(Classroom c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.id().toString());
        out.put("name", c.name());
        out.put("studentCount", c.studentCount());
        return out;
    }

    public Map<String, Object> toTeacherStudentVo(TeacherStudent ts) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", ts.studentId().toString());
        out.put("username", ts.username());
        out.put("classId", ts.classroomId() == null ? null : ts.classroomId().toString());
        out.put("className", ts.classroomName());
        return out;
    }

    public Map<String, Object> toTeacherSuggestionVo(TeacherSuggestion s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id().toString());
        out.put("teacherId", s.teacherId().toString());
        out.put("studentId", s.studentId().toString());
        out.put("questionId", s.questionId() == null ? null : s.questionId().toString());
        out.put("knowledgePoint", s.knowledgePoint());
        out.put("suggestion", s.suggestion());
        out.put("createdAt", ApiDataMapper.asIsoTime(s.createdAt()));
        return out;
    }

    public Map<String, Object> toAdminResourceVo(AdminResource r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resourceId", r.resourceId().toString());
        out.put("resourceType", r.resourceType());
        out.put("title", r.title());
        out.put("creatorId", r.creatorId() == null ? null : r.creatorId().toString());
        out.put("creatorUsername", r.creatorUsername());
        out.put("createdAt", ApiDataMapper.asIsoTime(r.createdAt()));
        return out;
    }

    public Map<String, Object> toAuditLogVo(AuditLog a) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", a.id().toString());
        out.put("actorId", a.actorId() == null ? null : a.actorId().toString());
        out.put("actorRole", a.actorRole());
        out.put("action", a.action());
        out.put("resourceType", a.resourceType());
        out.put("resourceId", a.resourceId());
        out.put("detail", ApiDataMapper.parseJsonValue(a.detailJson(), objectMapper));
        out.put("ip", a.ip());
        out.put("createdAt", ApiDataMapper.asIsoTime(a.createdAt()));
        return out;
    }

    public Map<String, Object> toWeakPointVo(WeakPoint wp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("knowledgePoint", wp.knowledgePoint());
        out.put("wrongCount", wp.wrongCount());
        out.put("errorRate", wp.errorRate());
        return out;
    }

    public Map<String, Object> toDashboardMetricsVo(DashboardMetrics m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalUsers", m.totalUsers());
        out.put("totalStudents", m.totalStudents());
        out.put("totalTeachers", m.totalTeachers());
        out.put("totalAdmins", m.totalAdmins());
        out.put("totalChatSessions", m.totalChatSessions());
        out.put("totalChatMessages", m.totalChatMessages());
        out.put("totalExerciseRecords", m.totalExerciseRecords());
        out.put("totalQuestions", m.totalQuestions());
        out.put("totalDocuments", m.totalDocuments());
        out.put("totalKnowledgeChunks", m.totalKnowledgeChunks());
        out.put("totalVectors", m.totalKnowledgeChunks());
        out.put("totalLessonPlans", m.totalLessonPlans());
        out.put("totalAiQuestionSessions", m.totalAiQuestionSessions());
        return out;
    }
}
