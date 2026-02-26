package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.service.AiClient;
import com.edunexus.api.service.DbService;
import com.edunexus.api.service.GovernanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/student")
public class StudentController implements ControllerSupport {
    private final DbService db;
    private final AiClient aiClient;
    private final GovernanceService governance;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public StudentController(DbService db, AiClient aiClient, GovernanceService governance) {
        this.db = db;
        this.aiClient = aiClient;
        this.governance = governance;
    }

    @PostMapping("/chat/session")
    public ResponseEntity<ApiResponse> createSession(HttpServletRequest request) {
        requireRole("STUDENT");
        UUID id = db.newId();
        db.update("insert into chat_sessions(id,student_id,title) values (?,?,?)", id, currentUser().userId(), "新建对话");
        governance.audit(currentUser().userId(), currentUser().role(), "CREATE_CHAT_SESSION", "CHAT_SESSION", id.toString(), trace(request));
        return ResponseEntity.status(201).body(ApiResponse.created(Map.of("sessionId", id), trace(request)));
    }

    @GetMapping("/chat/sessions")
    public ResponseEntity<ApiResponse> listSessions(@RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
                                                    @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
                                                    HttpServletRequest request) {
        requireRole("STUDENT");
        int offset = (page - 1) * size;
        List<Map<String, Object>> rows = db.list("select id as \"sessionId\", title, updated_at as \"updatedAt\" from chat_sessions where student_id=? and is_deleted=false order by updated_at desc limit ? offset ?",
                currentUser().userId(), size, offset);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", rows, "page", page, "size", size), trace(request)));
    }

    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse> getSession(@PathVariable("sessionId") UUID sessionId, HttpServletRequest request) {
        requireRole("STUDENT");
        ensureSessionOwner(sessionId, currentUser());
        Map<String, Object> session = db.one("select id as \"sessionId\", title, created_at as \"createdAt\" from chat_sessions where id=?", sessionId);
        List<Map<String, Object>> messages = db.list("select id as \"messageId\", role, content, citations, created_at as \"timestamp\" from chat_messages where session_id=? order by created_at asc", sessionId);
        session.put("messages", messages);
        return ResponseEntity.ok(ApiResponse.ok(session, trace(request)));
    }

    @DeleteMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse> deleteSession(@PathVariable("sessionId") UUID sessionId, HttpServletRequest request) {
        requireRole("STUDENT");
        ensureSessionOwner(sessionId, currentUser());
        db.update("update chat_sessions set is_deleted=true,deleted_at=now(),updated_at=now() where id=?", sessionId);
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @PostMapping("/chat/session/{sessionId}/message")
    @Transactional
    public ResponseEntity<?> sendMessage(@PathVariable("sessionId") UUID sessionId, @Valid @RequestBody ChatSendReq req, HttpServletRequest request) {
        requireRole("STUDENT");
        ensureSessionOwner(sessionId, currentUser());
        UUID userMsgId = db.newId();
        db.update("insert into chat_messages(id,session_id,role,content,citations) values (?,?, 'USER', ?, null)", userMsgId, sessionId, req.message());
        db.update("update chat_sessions set updated_at=now() where id=?", sessionId);

        Map<String, Object> aiReq = new HashMap<>();
        aiReq.put("student_id", currentUser().userId().toString());
        aiReq.put("session_id", sessionId.toString());
        aiReq.put("message", req.message());
        aiReq.put("trace_id", trace(request));
        aiReq.put("scene", "chat_rag");
        List<Map<String, Object>> rel = db.list("""
                select teacher_id
                from teacher_student_bindings
                where student_id=?
                  and status='ACTIVE'
                  and (revoked_at is null or revoked_at > now())
                order by coalesce(effective_from, created_at) asc
                limit 1
                """, currentUser().userId());
        if (!rel.isEmpty()) {
            aiReq.put("teacher_id", String.valueOf(rel.getFirst().get("teacher_id")));
        }
        Map<String, Object> aiResp = aiClient.chat(aiReq);

        String answer = String.valueOf(aiResp.getOrDefault("answer", "服务繁忙，请稍后重试"));
        Object citations = aiResp.getOrDefault("citations", List.of());
        UUID aiMsgId = db.newId();
        db.update("insert into chat_messages(id,session_id,role,content,citations) values (?,?, 'ASSISTANT', ?, ?::jsonb)",
                aiMsgId, sessionId, answer, toJson(citations));

        Map<String, Object> data = Map.of(
                "userMessage", req.message(),
                "aiResponse", answer,
                "sources", citations,
                "timestamp", Instant.now().toString()
        );
        updateSessionTitleIfNeeded(sessionId);

        String accept = Optional.ofNullable(request.getHeader("Accept")).orElse("");
        if (accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            String streamPayload = "data: " + toJson(Map.of("delta", answer, "citations", citations)) + "\n\n"
                    + "data: [DONE]\n\n";
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(streamPayload);
        }

        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/exercise/questions")
    public ResponseEntity<ApiResponse> listQuestions(@RequestParam(value = "subject", required = false) String subject,
                                                     @RequestParam(value = "difficulty", required = false) String difficulty,
                                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                                     @RequestParam(value = "size", defaultValue = "10") int size,
                                                     HttpServletRequest request) {
        requireRole("STUDENT");
        String sql = "select id as \"questionId\", subject, question_type as type, difficulty, content, options, knowledge_points as \"knowledgePoints\" from questions where is_active=true";
        List<Object> params = new ArrayList<>();
        if (subject != null && !subject.isBlank()) {
            sql += " and subject=?";
            params.add(subject);
        }
        if (difficulty != null && !difficulty.isBlank()) {
            sql += " and difficulty=?";
            params.add(difficulty);
        }
        sql += " order by created_at desc limit ? offset ?";
        params.add(size);
        params.add((page - 1) * size);
        List<Map<String, Object>> list = db.list(sql, params.toArray());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", list, "page", page, "size", size), trace(request)));
    }

    @PostMapping("/exercise/submit")
    @Transactional
    public ResponseEntity<ApiResponse> submitExercise(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                      @Valid @RequestBody ExerciseSubmitReq req,
                                                      HttpServletRequest request) {
        requireRole("STUDENT");
        if (req.answers() == null || req.answers().isEmpty()) throw new IllegalArgumentException("answers 不能为空");
        String requestHash = governance.requestHash(Map.of("studentId", currentUser().userId(), "payload", req));
        Map<String, Object> replay = governance.getIdempotentReplay("student.exercise.submit", idempotencyKey, requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }
        UUID recordId = db.newId();
        int totalScore = 0;
        int correctCount = 0;
        String subject = null;
        List<Map<String, Object>> results = new ArrayList<>();

        db.update("insert into exercise_records(id,student_id,subject,total_questions,correct_count,total_score,time_spent) values (?,?,?,?,?,?,?)",
                recordId, currentUser().userId(), null, req.answers().size(), 0, 0, req.timeSpent() == null ? 0 : req.timeSpent());

        for (AnswerItem ans : req.answers()) {
            Map<String, Object> q = db.one("select id,subject,correct_answer,analysis,score from questions where id=?", UUID.fromString(ans.questionId()));
            if (subject == null) subject = String.valueOf(q.get("subject"));
            boolean correct = String.valueOf(q.get("correct_answer")).trim().equalsIgnoreCase(ans.userAnswer().trim());
            int score = correct ? ((Number) q.get("score")).intValue() : 0;
            if (correct) correctCount++;
            totalScore += score;

            UUID itemId = db.newId();
            db.update("insert into exercise_record_items(id,record_id,question_id,user_answer,correct_answer,is_correct,score,analysis) values (?,?,?,?,?,?,?,?)",
                    itemId, recordId, UUID.fromString(ans.questionId()), ans.userAnswer(), String.valueOf(q.get("correct_answer")), correct, score, String.valueOf(q.get("analysis")));

            if (!correct) {
                boolean exists = db.exists("select 1 from wrong_book where student_id=? and question_id=?", currentUser().userId(), UUID.fromString(ans.questionId()));
                if (exists) {
                    db.update("update wrong_book set wrong_count=wrong_count+1,last_wrong_time=now(),status='ACTIVE',updated_at=now() where student_id=? and question_id=?",
                            currentUser().userId(), UUID.fromString(ans.questionId()));
                } else {
                    db.update("insert into wrong_book(id,student_id,question_id,wrong_count,status) values (?,?,?,1,'ACTIVE')",
                            db.newId(), currentUser().userId(), UUID.fromString(ans.questionId()));
                }
            }
            results.add(Map.of(
                    "questionId", ans.questionId(),
                    "isCorrect", correct,
                    "correctAnswer", String.valueOf(q.get("correct_answer")),
                    "userAnswer", ans.userAnswer(),
                    "score", score
            ));
        }

        db.update("update exercise_records set subject=?,correct_count=?,total_score=? where id=?",
                subject, correctCount, totalScore, recordId);

        Map<String, Object> data = Map.of(
                "recordId", recordId,
                "submitTime", Instant.now().toString(),
                "results", results,
                "totalScore", totalScore,
                "correctCount", correctCount,
                "totalCount", req.answers().size()
        );
        governance.storeIdempotency("student.exercise.submit", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(currentUser().userId(), currentUser().role(), "SUBMIT_EXERCISE", "EXERCISE_RECORD", recordId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/exercise/{recordId}/analysis")
    public ResponseEntity<ApiResponse> exerciseAnalysis(@PathVariable("recordId") UUID recordId, HttpServletRequest request) {
        requireRole("STUDENT");
        Map<String, Object> record = db.one("select id,student_id,created_at from exercise_records where id=?", recordId);
        if (!currentUser().userId().equals(record.get("student_id"))) throw new SecurityException("无权访问该记录");
        List<Map<String, Object>> items = db.list("""
                select i.question_id as \"questionId\", q.content, q.options, i.user_answer as \"userAnswer\", i.correct_answer as \"correctAnswer\",
                       i.is_correct as \"isCorrect\", coalesce(i.analysis,q.analysis) as analysis, q.knowledge_points as \"knowledgePoints\",
                       (select suggestion from teacher_suggestions s where s.student_id=? and (
                            s.question_id=i.question_id
                            or (s.knowledge_point is not null and jsonb_exists(q.knowledge_points, s.knowledge_point))
                        )
                          order by s.created_at desc limit 1) as \"teacherSuggestion\"
                from exercise_record_items i join questions q on i.question_id=q.id
                where i.record_id=? order by i.created_at
                """, currentUser().userId(), recordId);
        Map<String, Object> data = new HashMap<>();
        data.put("recordId", recordId);
        data.put("submitTime", String.valueOf(record.get("created_at")));
        data.put("questions", items);
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/exercise/wrong-questions")
    public ResponseEntity<ApiResponse> wrongQuestions(@RequestParam(value = "subject", required = false) String subject,
                                                      @RequestParam(value = "status", defaultValue = "ACTIVE") String status,
                                                      @RequestParam(value = "page", defaultValue = "1") int page,
                                                      @RequestParam(value = "size", defaultValue = "20") int size,
                                                      HttpServletRequest request) {
        requireRole("STUDENT");
        if (!"ACTIVE".equals(status) && !"MASTERED".equals(status)) {
            throw new IllegalArgumentException("status 仅支持 ACTIVE/MASTERED");
        }
        String sql = """
                select w.id, w.question_id as \"questionId\", q.subject, q.content, w.wrong_count as \"wrongCount\", w.last_wrong_time as \"lastWrongTime\",
                       q.knowledge_points as \"knowledgePoints\",
                       (select suggestion from teacher_suggestions s where s.student_id=w.student_id and (
                            s.question_id=w.question_id
                            or (s.knowledge_point is not null and jsonb_exists(q.knowledge_points, s.knowledge_point))
                        )
                          order by s.created_at desc limit 1) as \"teacherSuggestion\"
                from wrong_book w join questions q on w.question_id=q.id
                where w.student_id=? and w.status=?
                """;
        List<Object> args = new ArrayList<>();
        args.add(currentUser().userId());
        args.add(status);
        if (subject != null && !subject.isBlank()) {
            sql += " and q.subject=?";
            args.add(subject);
        }
        sql += " order by w.last_wrong_time desc limit ? offset ?";
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> list = db.list(sql, args.toArray());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", list, "page", page, "size", size), trace(request)));
    }

    @DeleteMapping("/exercise/wrong-questions/{questionId}")
    public ResponseEntity<ApiResponse> removeWrongQuestion(@PathVariable("questionId") UUID questionId, HttpServletRequest request) {
        requireRole("STUDENT");
        db.update("update wrong_book set status='MASTERED',updated_at=now() where student_id=? and question_id=?", currentUser().userId(), questionId);
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @GetMapping("/exercise/records")
    public ResponseEntity<ApiResponse> records(@RequestParam(value = "startDate", required = false) String startDate,
                                               @RequestParam(value = "endDate", required = false) String endDate,
                                               @RequestParam(value = "page", defaultValue = "1") int page,
                                               @RequestParam(value = "size", defaultValue = "15") int size,
                                               HttpServletRequest request) {
        requireRole("STUDENT");
        String sql = "select id as \"recordId\", created_at as \"submitTime\", total_questions as \"totalQuestions\", correct_count as \"correctCount\", total_score as \"totalScore\", time_spent as \"timeSpent\", subject from exercise_records where student_id=?";
        List<Object> args = new ArrayList<>();
        args.add(currentUser().userId());
        if (startDate != null && !startDate.isBlank()) {
            sql += " and created_at >= ?::date";
            args.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            sql += " and created_at < (?::date + interval '1 day')";
            args.add(endDate);
        }
        sql += " order by created_at desc limit ? offset ?";
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> list = db.list(sql, args.toArray());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", list, "page", page, "size", size), trace(request)));
    }

    @PostMapping("/ai-questions/generate")
    @Transactional
    public ResponseEntity<ApiResponse> generateAiQuestions(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                           @Valid @RequestBody AiGenerateReq req,
                                                           HttpServletRequest request) {
        requireRole("STUDENT");
        String requestHash = governance.requestHash(Map.of("studentId", currentUser().userId(), "payload", req));
        Map<String, Object> replay = governance.getIdempotentReplay("student.aiq.generate", idempotencyKey, requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }
        String difficulty = req.difficulty() == null || req.difficulty().isBlank() ? "MEDIUM" : req.difficulty();
        List<Map<String, Object>> wrong = db.list("""
                select q.knowledge_points from wrong_book w join questions q on w.question_id=q.id
                where w.student_id=? and w.status='ACTIVE' order by w.last_wrong_time desc limit 20
                """, currentUser().userId());
        List<Map<String, Object>> suggestions = db.list("select suggestion,knowledge_point from teacher_suggestions where student_id=? order by created_at desc limit 10", currentUser().userId());
        UUID sessionId = db.newId();
        String contextSnapshot = toJson(Map.of(
                "wrong_context", wrong,
                "teacher_suggestions", suggestions,
                "concept_tags", req.conceptTags() == null ? List.of() : req.conceptTags()
        ));
        db.update("insert into ai_question_sessions(id,student_id,subject,difficulty,question_count,context_snapshot) values (?,?,?,?,?,?::jsonb)",
                sessionId, currentUser().userId(), req.subject(), difficulty, req.count(), contextSnapshot);
        Map<String, Object> aiReq = new HashMap<>();
        aiReq.put("subject", req.subject());
        aiReq.put("difficulty", difficulty);
        aiReq.put("count", req.count());
        aiReq.put("concept_tags", req.conceptTags());
        aiReq.put("wrong_context", wrong);
        aiReq.put("teacher_suggestions", suggestions);
        aiReq.put("trace_id", trace(request));
        aiReq.put("scene", "ai_question");
        aiReq.put("student_id", currentUser().userId().toString());
        Map<String, Object> aiResp = aiClient.generateQuestions(aiReq);
        List<Map<String, Object>> questions = (List<Map<String, Object>>) aiResp.getOrDefault("questions", List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            UUID qid = db.newId();
            db.update("insert into questions(id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,ai_session_id,created_by) values (?,?,?,?,?,?::jsonb,?,?,?::jsonb,5,'AI_GENERATED',?,?)",
                    qid,
                    req.subject(),
                    String.valueOf(q.getOrDefault("question_type", "SINGLE_CHOICE")),
                    difficulty,
                    String.valueOf(q.getOrDefault("content", "")),
                    toJson(q.getOrDefault("options", Map.of())),
                    String.valueOf(q.getOrDefault("correct_answer", "")),
                    String.valueOf(q.getOrDefault("explanation", "")),
                    toJson(q.getOrDefault("knowledge_points", List.of())),
                    sessionId,
                    currentUser().userId());
            out.add(Map.of(
                    "questionId", qid,
                    "subject", req.subject(),
                    "type", String.valueOf(q.getOrDefault("question_type", "SINGLE_CHOICE")),
                    "difficulty", difficulty,
                    "content", String.valueOf(q.getOrDefault("content", "")),
                    "options", q.getOrDefault("options", Map.of()),
                    "knowledgePoints", q.getOrDefault("knowledge_points", List.of())
            ));
        }
        Map<String, Object> data = Map.of("sessionId", sessionId, "generatedCount", out.size(), "questions", out);
        governance.storeIdempotency("student.aiq.generate", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(currentUser().userId(), currentUser().role(), "GENERATE_AIQ", "AIQ_SESSION", sessionId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/ai-questions")
    public ResponseEntity<ApiResponse> listAiSessions(@RequestParam(value = "subject", required = false) String subject,
                                                      @RequestParam(value = "page", defaultValue = "1") int page,
                                                      @RequestParam(value = "size", defaultValue = "10") int size,
                                                      HttpServletRequest request) {
        requireRole("STUDENT");
        String sql = "select id as \"sessionId\", subject, question_count as \"questionCount\", generated_at as \"generatedAt\", completed, correct_rate as \"correctRate\", score from ai_question_sessions where student_id=?";
        List<Object> args = new ArrayList<>();
        args.add(currentUser().userId());
        if (subject != null && !subject.isBlank()) {
            sql += " and subject=?";
            args.add(subject);
        }
        sql += " order by generated_at desc limit ? offset ?";
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> list = db.list(sql, args.toArray());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", list, "page", page, "size", size), trace(request)));
    }

    @PostMapping("/ai-questions/submit")
    @Transactional
    public ResponseEntity<ApiResponse> submitAiQuestions(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                         @Valid @RequestBody AiSubmitReq req,
                                                         HttpServletRequest request) {
        requireRole("STUDENT");
        String requestHash = governance.requestHash(Map.of("studentId", currentUser().userId(), "payload", req));
        Map<String, Object> replay = governance.getIdempotentReplay("student.aiq.submit", idempotencyKey, requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }
        Map<String, Object> session = db.one("select id,student_id from ai_question_sessions where id=?", UUID.fromString(req.sessionId()));
        if (!currentUser().userId().equals(session.get("student_id"))) throw new SecurityException("无权访问该会话");

        UUID recordId = db.newId();
        int correct = 0;
        int totalScore = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        db.update("insert into ai_question_records(id,session_id,student_id,total_questions,correct_count,total_score) values (?,?,?,?,?,?)",
                recordId, UUID.fromString(req.sessionId()), currentUser().userId(), req.answers().size(), 0, 0);

        for (AnswerItem item : req.answers()) {
            Map<String, Object> q = db.one("select id,correct_answer,analysis,score from questions where id=?", UUID.fromString(item.questionId()));
            boolean isCorrect = String.valueOf(q.get("correct_answer")).trim().equalsIgnoreCase(item.userAnswer().trim());
            int score = isCorrect ? ((Number) q.get("score")).intValue() : 0;
            if (isCorrect) correct++;
            totalScore += score;
            db.update("insert into ai_question_record_items(id,record_id,question_id,user_answer,correct_answer,is_correct,score,analysis) values (?,?,?,?,?,?,?,?)",
                    db.newId(), recordId, UUID.fromString(item.questionId()), item.userAnswer(), String.valueOf(q.get("correct_answer")), isCorrect, score, String.valueOf(q.get("analysis")));
            results.add(Map.of(
                    "questionId", item.questionId(),
                    "isCorrect", isCorrect,
                    "correctAnswer", String.valueOf(q.get("correct_answer")),
                    "userAnswer", item.userAnswer(),
                    "score", score
            ));
        }
        db.update("update ai_question_records set correct_count=?,total_score=? where id=?", correct, totalScore, recordId);

        BigDecimal rate = req.answers().isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf((double) correct * 100.0 / req.answers().size());
        db.update("update ai_question_sessions set completed=true,correct_rate=?,score=?,updated_at=now() where id=?",
                rate, totalScore, UUID.fromString(req.sessionId()));

        Map<String, Object> data = Map.of(
                "recordId", recordId,
                "sessionId", req.sessionId(),
                "submitTime", Instant.now().toString(),
                "results", results,
                "totalScore", totalScore,
                "correctCount", correct,
                "totalCount", req.answers().size()
        );
        governance.storeIdempotency("student.aiq.submit", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(currentUser().userId(), currentUser().role(), "SUBMIT_AIQ", "AIQ_RECORD", recordId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/ai-questions/{recordId}/analysis")
    public ResponseEntity<ApiResponse> aiQuestionAnalysis(@PathVariable("recordId") UUID recordId, HttpServletRequest request) {
        requireRole("STUDENT");
        Map<String, Object> record = db.one("select id,student_id,session_id,submitted_at from ai_question_records where id=?", recordId);
        if (!currentUser().userId().equals(record.get("student_id"))) throw new SecurityException("无权访问该记录");
        List<Map<String, Object>> qs = db.list("""
                select q.id as \"questionId\", q.content, q.options, i.user_answer as \"userAnswer\", i.correct_answer as \"correctAnswer\",
                       i.is_correct as \"isCorrect\", coalesce(i.analysis,q.analysis) as analysis, q.knowledge_points as \"knowledgePoints\"
                from ai_question_record_items i
                join questions q on q.id=i.question_id
                where i.record_id=? order by i.created_at asc
                """, recordId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "recordId", recordId,
                "sessionId", String.valueOf(record.get("session_id")),
                "submitTime", String.valueOf(record.get("submitted_at")),
                "questions", qs
        ), trace(request)));
    }

    private void ensureSessionOwner(UUID sessionId, AuthUser user) {
        Map<String, Object> session = db.one("select student_id,is_deleted from chat_sessions where id=?", sessionId);
        if (Boolean.TRUE.equals(session.get("is_deleted"))) throw new IllegalArgumentException("会话不存在");
        if (!user.userId().equals(session.get("student_id"))) throw new SecurityException("无权访问该会话");
    }

    private void updateSessionTitleIfNeeded(UUID sessionId) {
        List<Map<String, Object>> firstUser = db.list("select content from chat_messages where session_id=? and role='USER' order by created_at asc limit 1", sessionId);
        if (firstUser.isEmpty()) return;
        String title = String.valueOf(firstUser.getFirst().get("content"));
        title = title.length() > 20 ? title.substring(0, 20) : title;
        db.update("update chat_sessions set title=?,updated_at=now() where id=? and title='新建对话'", title, sessionId);
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String s && (s.startsWith("{") || s.startsWith("["))) return s;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "null";
        }
    }

    public record ChatSendReq(@NotBlank @Size(max = 4000) String message) {}
    public record ExerciseSubmitReq(@NotNull List<@Valid AnswerItem> answers, Integer timeSpent) {}
    public record AiGenerateReq(@Min(1) @Max(20) int count,
                                @NotBlank String subject,
                                @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
                                List<String> conceptTags) {}
    public record AiSubmitReq(@NotBlank String sessionId, @NotNull List<@Valid AnswerItem> answers) {}
    public record AnswerItem(@NotBlank String questionId, @NotBlank String userAnswer) {}
}
