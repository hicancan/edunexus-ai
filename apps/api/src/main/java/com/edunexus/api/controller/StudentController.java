package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.common.Difficulty;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.service.AiClient;
import com.edunexus.api.service.DbService;
import com.edunexus.api.service.GovernanceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/student")
public class StudentController implements ControllerSupport {
    private final DbService db;
    private final AiClient aiClient;
    private final GovernanceService governance;
    private final ObjectMapper objectMapper;

    public StudentController(DbService db, AiClient aiClient, GovernanceService governance, ObjectMapper objectMapper) {
        this.db = db;
        this.aiClient = aiClient;
        this.governance = governance;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat/session")
    public ResponseEntity<ApiResponse> createSession(HttpServletRequest request) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        UUID sessionId = db.newId();
        db.update(
                "insert into chat_sessions(id,student_id,title) values (?,?,?)",
                sessionId,
                user.userId(),
                "新建对话"
        );
        governance.audit(user.userId(), user.role(), "CREATE_CHAT_SESSION", "CHAT_SESSION", sessionId.toString(), trace(request));

        Map<String, Object> vo = db.one("select id,student_id,title,created_at,updated_at from chat_sessions where id=?", sessionId);
        return ResponseEntity.status(201).body(ApiResponse.created(toChatSessionVo(vo), trace(request)));
    }

    @GetMapping("/chat/sessions")
    public ResponseEntity<ApiResponse> listSessions(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        int offset = (page - 1) * size;

        List<Map<String, Object>> rows = db.list(
                """
                select id,student_id,title,created_at,updated_at
                from chat_sessions
                where student_id=? and is_deleted=false
                order by updated_at desc
                limit ? offset ?
                """,
                user.userId(),
                size,
                offset
        );
        long total = db.count("select count(*) from chat_sessions where student_id=? and is_deleted=false", user.userId());
        List<Map<String, Object>> content = rows.stream().map(this::toChatSessionVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse> getSession(@PathVariable("sessionId") UUID sessionId, HttpServletRequest request) {
        requireRole("STUDENT");
        ensureSessionOwner(sessionId);

        Map<String, Object> session = db.one("select id,title,created_at from chat_sessions where id=? and is_deleted=false", sessionId);
        List<Map<String, Object>> messageRows = db.list(
                """
                select id,role,content,citations,token_usage,created_at
                from chat_messages
                where session_id=?
                order by created_at asc
                """,
                sessionId
        );

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", String.valueOf(session.get("id")));
        detail.put("title", String.valueOf(session.get("title")));
        detail.put("createdAt", ApiDataMapper.asIsoTime(session.get("created_at")));
        detail.put("messages", messageRows.stream().map(this::toChatMessageVo).toList());

        return ResponseEntity.ok(ApiResponse.ok(detail, trace(request)));
    }

    @DeleteMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse> deleteSession(@PathVariable("sessionId") UUID sessionId, HttpServletRequest request) {
        requireRole("STUDENT");
        ensureSessionOwner(sessionId);
        db.update("update chat_sessions set is_deleted=true,deleted_at=now(),updated_at=now() where id=?", sessionId);
        governance.audit(currentUser().userId(), currentUser().role(), "DELETE_CHAT_SESSION", "CHAT_SESSION", sessionId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @PostMapping("/chat/session/{sessionId}/message")
    @Transactional
    public ResponseEntity<?> sendMessage(
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody ChatSendReq req,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        ensureSessionOwner(sessionId);

        UUID userMessageId = db.newId();
        db.update(
                "insert into chat_messages(id,session_id,role,content,citations,token_usage) values (?,?, 'USER', ?, null, 0)",
                userMessageId,
                sessionId,
                req.message()
        );

        List<Map<String, Object>> historyRows = db.list(
                """
                select role,content
                from chat_messages
                where session_id=?
                order by created_at asc
                limit 30
                """,
                sessionId
        );
        List<Map<String, Object>> history = historyRows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", String.valueOf(row.get("role")));
            item.put("content", String.valueOf(row.get("content")));
            return item;
        }).toList();

        Map<String, Object> teacherBinding = db.oneOrNull(
                """
                select teacher_id,classroom_id
                from teacher_student_bindings
                where student_id=?
                  and status='ACTIVE'
                  and (revoked_at is null or revoked_at > now())
                order by coalesce(effective_from, created_at) asc
                limit 1
                """,
                user.userId()
        );

        Map<String, Object> chatBody = new HashMap<>();
        chatBody.put("traceId", trace(request));
        chatBody.put("sessionId", sessionId.toString());
        chatBody.put("studentId", user.userId().toString());
        chatBody.put("message", req.message());
        chatBody.put("stream", false);
        chatBody.put("context", Map.of("history", history));
        Map<String, Object> teacherScope = new LinkedHashMap<>();
        teacherScope.put("teacherId", teacherBinding == null ? null : String.valueOf(teacherBinding.get("teacher_id")));
        teacherScope.put("classId",
                teacherBinding == null || teacherBinding.get("classroom_id") == null
                        ? null
                        : String.valueOf(teacherBinding.get("classroom_id")));
        chatBody.put("teacherScope", teacherScope);

        Map<String, Object> aiResult = aiClient.chat(chatBody);
        String answer = String.valueOf(aiResult.getOrDefault("answer", "课堂资料不足，暂时无法给出可靠答案。"));
        List<Map<String, Object>> citations = ApiDataMapper.parseObjectList(aiResult.get("citations"), objectMapper);
        int tokenUsage = parseTokenUsage(aiResult.get("tokenUsage"));

        UUID assistantMessageId = db.newId();
        db.update(
                "insert into chat_messages(id,session_id,role,content,citations,token_usage) values (?,?, 'ASSISTANT', ?, ?::jsonb, ?)",
                assistantMessageId,
                sessionId,
                answer,
                toJson(citations),
                tokenUsage
        );
        db.update("update chat_sessions set updated_at=now() where id=?", sessionId);
        updateSessionTitleIfNeeded(sessionId);

        Map<String, Object> userMessage = db.one("select id,role,content,citations,token_usage,created_at from chat_messages where id=?", userMessageId);
        Map<String, Object> assistantMessage = db.one("select id,role,content,citations,token_usage,created_at from chat_messages where id=?", assistantMessageId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userMessage", toChatMessageVo(userMessage));
        data.put("assistantMessage", toChatMessageVo(assistantMessage));

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            String streamPayload = "data: " + toJson(Map.of("delta", answer, "citations", citations)) + "\n\n"
                    + "data: [DONE]\n\n";
            return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(streamPayload);
        }

        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/exercise/questions")
    public ResponseEntity<ApiResponse> listQuestions(
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        Difficulty parsedDifficulty = Difficulty.fromString(difficulty);

        StringBuilder where = new StringBuilder(" where is_active=true");
        List<Object> args = new ArrayList<>();
        if (subject != null && !subject.isBlank()) {
            where.append(" and subject=?");
            args.add(subject);
        }
        if (parsedDifficulty != null) {
            where.append(" and difficulty=?");
            args.add(parsedDifficulty.name());
        }

        int offset = (page - 1) * size;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add(offset);

        List<Map<String, Object>> rows = db.list(
                """
                select id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,created_at
                from questions
                """ + where + " order by created_at desc limit ? offset ?",
                listArgs.toArray()
        );
        long total = db.count("select count(*) from questions" + where, args.toArray());

        List<Map<String, Object>> content = rows.stream().map(this::toQuestionVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PostMapping("/exercise/submit")
    @Transactional
    public ResponseEntity<ApiResponse> submitExercise(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExerciseSubmitReq req,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        AuthUser user = currentUser();

        String requestHash = governance.requestHash(Map.of("studentId", user.userId(), "payload", req));
        Map<String, Object> replay = governance.getIdempotentReplay("student.exercise.submit", idempotencyKey, requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }

        UUID recordId = db.newId();
        db.update(
                "insert into exercise_records(id,student_id,subject,total_questions,correct_count,total_score,time_spent) values (?,?,?,?,?,?,?)",
                recordId,
                user.userId(),
                null,
                req.answers().size(),
                0,
                0,
                req.timeSpent() == null ? 0 : req.timeSpent()
        );

        int correctCount = 0;
        int totalScore = 0;
        String subject = null;
        List<Map<String, Object>> items = new ArrayList<>();

        for (AnswerItem answerItem : req.answers()) {
            UUID questionId = parseUuid(answerItem.questionId(), "questionId");
            Map<String, Object> question = db.one(
                    "select id,subject,correct_answer,analysis,score from questions where id=? and is_active=true",
                    questionId
            );
            if (subject == null) {
                subject = String.valueOf(question.get("subject"));
            }

            String correctAnswer = String.valueOf(question.get("correct_answer"));
            boolean isCorrect = correctAnswer.trim().equalsIgnoreCase(answerItem.userAnswer().trim());
            int score = isCorrect ? ApiDataMapper.asInt(question.get("score")) : 0;
            if (isCorrect) {
                correctCount++;
            }
            totalScore += score;

            String teacherSuggestion = fetchSuggestionByQuestion(user.userId(), questionId);
            db.update(
                    """
                    insert into exercise_record_items(id,record_id,question_id,user_answer,correct_answer,is_correct,score,analysis,teacher_suggestion)
                    values (?,?,?,?,?,?,?,?,?)
                    """,
                    db.newId(),
                    recordId,
                    questionId,
                    answerItem.userAnswer(),
                    correctAnswer,
                    isCorrect,
                    score,
                    ApiDataMapper.asString(question.get("analysis")),
                    teacherSuggestion
            );

            if (!isCorrect) {
                upsertWrongBook(user.userId(), questionId);
            }

            items.add(Map.of(
                    "questionId", questionId.toString(),
                    "userAnswer", answerItem.userAnswer(),
                    "correctAnswer", correctAnswer,
                    "isCorrect", isCorrect,
                    "score", score
            ));
        }

        db.update(
                "update exercise_records set subject=?,correct_count=?,total_score=? where id=?",
                subject,
                correctCount,
                totalScore,
                recordId
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordId", recordId.toString());
        data.put("totalQuestions", req.answers().size());
        data.put("correctCount", correctCount);
        data.put("totalScore", totalScore);
        data.put("items", items);

        governance.storeIdempotency("student.exercise.submit", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(user.userId(), user.role(), "SUBMIT_EXERCISE", "EXERCISE_RECORD", recordId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/exercise/{recordId}/analysis")
    public ResponseEntity<ApiResponse> exerciseAnalysis(@PathVariable("recordId") UUID recordId, HttpServletRequest request) {
        requireRole("STUDENT");
        ensureExerciseRecordOwner(recordId);
        AuthUser user = currentUser();

        List<Map<String, Object>> rows = db.list(
                """
                select
                  i.question_id,
                  q.content,
                  i.user_answer,
                  i.correct_answer,
                  i.is_correct,
                  coalesce(i.analysis, q.analysis) as analysis,
                  q.knowledge_points,
                  coalesce(
                    i.teacher_suggestion,
                    (
                      select s.suggestion
                      from teacher_suggestions s
                      where s.student_id=?
                        and (
                          s.question_id=i.question_id
                          or (
                            s.knowledge_point is not null
                            and q.knowledge_points is not null
                            and jsonb_exists(q.knowledge_points, s.knowledge_point)
                          )
                        )
                      order by s.created_at desc
                      limit 1
                    )
                  ) as teacher_suggestion
                from exercise_record_items i
                join questions q on q.id=i.question_id
                where i.record_id=?
                order by i.created_at asc
                """,
                user.userId(),
                recordId
        );

        List<Map<String, Object>> items = rows.stream().map(this::toExerciseAnalysisItemVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "recordId", recordId.toString(),
                "items", items
        ), trace(request)));
    }

    @GetMapping("/exercise/wrong-questions")
    public ResponseEntity<ApiResponse> wrongQuestions(
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        if (!"ACTIVE".equals(status) && !"MASTERED".equals(status)) {
            throw new IllegalArgumentException("status 仅支持 ACTIVE/MASTERED");
        }

        AuthUser user = currentUser();
        StringBuilder where = new StringBuilder(" where w.student_id=? and w.status=?");
        List<Object> args = new ArrayList<>();
        args.add(user.userId());
        args.add(status);
        if (subject != null && !subject.isBlank()) {
            where.append(" and q.subject=?");
            args.add(subject);
        }

        int offset = (page - 1) * size;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add(offset);

        List<Map<String, Object>> rows = db.list(
                """
                select
                  w.id,
                  w.question_id,
                  w.wrong_count,
                  w.last_wrong_time,
                  w.status,
                  q.subject,
                  q.question_type,
                  q.difficulty,
                  q.content,
                  q.options,
                  q.correct_answer,
                  q.analysis,
                  q.knowledge_points,
                  q.score,
                  q.source,
                  q.created_at
                from wrong_book w
                join questions q on q.id=w.question_id
                """ + where + " order by w.last_wrong_time desc limit ? offset ?",
                listArgs.toArray()
        );

        long total = db.count(
                "select count(*) from wrong_book w join questions q on q.id=w.question_id" + where,
                args.toArray()
        );

        List<Map<String, Object>> content = rows.stream().map(this::toWrongBookEntryVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @DeleteMapping("/exercise/wrong-questions/{questionId}")
    public ResponseEntity<ApiResponse> removeWrongQuestion(@PathVariable("questionId") UUID questionId, HttpServletRequest request) {
        requireRole("STUDENT");
        int updated = db.update(
                "update wrong_book set status='MASTERED',updated_at=now() where student_id=? and question_id=?",
                currentUser().userId(),
                questionId
        );
        if (updated == 0) {
            throw new ResourceNotFoundException("资源不存在");
        }
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @GetMapping("/exercise/records")
    public ResponseEntity<ApiResponse> records(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        AuthUser user = currentUser();

        if (startDate != null && !startDate.isBlank()) {
            LocalDate.parse(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            LocalDate.parse(endDate);
        }

        StringBuilder where = new StringBuilder(" where student_id=?");
        List<Object> args = new ArrayList<>();
        args.add(user.userId());
        if (startDate != null && !startDate.isBlank()) {
            where.append(" and created_at >= ?::date");
            args.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            where.append(" and created_at < (?::date + interval '1 day')");
            args.add(endDate);
        }

        int offset = (page - 1) * size;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add(offset);
        List<Map<String, Object>> rows = db.list(
                """
                select id,subject,total_questions,correct_count,total_score,time_spent,created_at
                from exercise_records
                """ + where + " order by created_at desc limit ? offset ?",
                listArgs.toArray()
        );
        long total = db.count("select count(*) from exercise_records" + where, args.toArray());

        List<Map<String, Object>> content = rows.stream().map(this::toExerciseRecordVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PostMapping("/ai-questions/generate")
    @Transactional
    public ResponseEntity<ApiResponse> generateAiQuestions(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AiGenerateReq req,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        Difficulty parsedDifficulty = Difficulty.fromString(req.difficulty());

        String requestHash = governance.requestHash(Map.of("studentId", user.userId(), "payload", req));
        Map<String, Object> replay = governance.getIdempotentReplay("student.aiq.generate", idempotencyKey, requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }

        String difficulty = parsedDifficulty == null ? Difficulty.MEDIUM.name() : parsedDifficulty.name();
        List<Map<String, Object>> weaknessProfile = db.list(
                """
                select q.knowledge_points as knowledge_points, w.wrong_count as wrong_count
                from wrong_book w
                join questions q on q.id=w.question_id
                where w.student_id=? and w.status='ACTIVE'
                order by w.last_wrong_time desc
                limit 20
                """,
                user.userId()
        );
        List<Map<String, Object>> suggestions = db.list(
                "select suggestion,knowledge_point from teacher_suggestions where student_id=? order by created_at desc limit 10",
                user.userId()
        );

        UUID sessionId = db.newId();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("conceptTags", req.conceptTags() == null ? List.of() : req.conceptTags());
        snapshot.put("weaknessProfile", weaknessProfile);
        snapshot.put("teacherSuggestions", suggestions);

        db.update(
                "insert into ai_question_sessions(id,student_id,subject,difficulty,question_count,context_snapshot) values (?,?,?,?,?,?::jsonb)",
                sessionId,
                user.userId(),
                req.subject(),
                difficulty,
                req.count(),
                toJson(snapshot)
        );

        Map<String, Object> aiBody = new HashMap<>();
        aiBody.put("traceId", trace(request));
        aiBody.put("studentId", user.userId().toString());
        aiBody.put("count", req.count());
        aiBody.put("subject", req.subject());
        aiBody.put("difficulty", difficulty);
        aiBody.put("conceptTags", req.conceptTags() == null ? List.of() : req.conceptTags());
        aiBody.put("weaknessProfile", weaknessProfile);
        aiBody.put("teacherSuggestions", suggestions);
        aiBody.put("idempotencyKey", idempotencyKey == null ? "" : idempotencyKey);
        Map<String, Object> aiResult = aiClient.generateQuestions(aiBody);

        List<Map<String, Object>> generated = ApiDataMapper.parseObjectList(aiResult.get("questions"), objectMapper);
        List<Map<String, Object>> questionVos = new ArrayList<>();

        for (Map<String, Object> generatedQuestion : generated) {
            UUID questionId = db.newId();
            String questionType = String.valueOf(generatedQuestion.getOrDefault("question_type", "SINGLE_CHOICE"));
            String content = String.valueOf(generatedQuestion.getOrDefault("content", "")).trim();
            String correctAnswer = String.valueOf(generatedQuestion.getOrDefault("correct_answer", "")).trim();
            if (content.isBlank() || correctAnswer.isBlank()) {
                continue;
            }

            db.update(
                    """
                    insert into questions(
                      id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,ai_session_id,created_by
                    ) values (?,?,?,?,?,?::jsonb,?,?,?::jsonb,5,'AI_GENERATED',?,?)
                    """,
                    questionId,
                    req.subject(),
                    questionType,
                    difficulty,
                    content,
                    toJson(generatedQuestion.getOrDefault("options", Map.of())),
                    correctAnswer,
                    String.valueOf(generatedQuestion.getOrDefault("explanation", "")),
                    toJson(generatedQuestion.getOrDefault("knowledge_points", List.of())),
                    sessionId,
                    user.userId()
            );

            Map<String, Object> row = db.one(
                    """
                    select id,subject,question_type,difficulty,content,options,correct_answer,analysis,knowledge_points,score,source,created_at
                    from questions where id=?
                    """,
                    questionId
            );
            questionVos.add(toQuestionVo(row));
        }

        db.update("update ai_question_sessions set question_count=?,updated_at=now() where id=?", questionVos.size(), sessionId);

        Map<String, Object> data = Map.of(
                "sessionId", sessionId.toString(),
                "questions", questionVos
        );
        governance.storeIdempotency("student.aiq.generate", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(user.userId(), user.role(), "GENERATE_AIQ", "AIQ_SESSION", sessionId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/ai-questions")
    public ResponseEntity<ApiResponse> listAiSessions(
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        AuthUser user = currentUser();

        StringBuilder where = new StringBuilder(" where student_id=?");
        List<Object> args = new ArrayList<>();
        args.add(user.userId());
        if (subject != null && !subject.isBlank()) {
            where.append(" and subject=?");
            args.add(subject);
        }

        int offset = (page - 1) * size;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add(offset);
        List<Map<String, Object>> rows = db.list(
                """
                select id,subject,difficulty,question_count,completed,correct_rate,score,generated_at
                from ai_question_sessions
                """ + where + " order by generated_at desc limit ? offset ?",
                listArgs.toArray()
        );
        long total = db.count("select count(*) from ai_question_sessions" + where, args.toArray());

        List<Map<String, Object>> content = rows.stream().map(this::toAiSessionVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PostMapping("/ai-questions/submit")
    @Transactional
    public ResponseEntity<ApiResponse> submitAiQuestions(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AiSubmitReq req,
            HttpServletRequest request
    ) {
        requireRole("STUDENT");
        AuthUser user = currentUser();

        UUID sessionId = parseUuid(req.sessionId(), "sessionId");
        ensureAiSessionOwner(sessionId);

        String requestHash = governance.requestHash(Map.of("studentId", user.userId(), "payload", req));
        Map<String, Object> replay = governance.getIdempotentReplay("student.aiq.submit", idempotencyKey, requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }

        UUID recordId = db.newId();
        db.update(
                "insert into ai_question_records(id,session_id,student_id,total_questions,correct_count,total_score) values (?,?,?,?,?,?)",
                recordId,
                sessionId,
                user.userId(),
                req.answers().size(),
                0,
                0
        );

        int correctCount = 0;
        int totalScore = 0;
        List<Map<String, Object>> items = new ArrayList<>();

        for (AnswerItem answerItem : req.answers()) {
            UUID questionId = parseUuid(answerItem.questionId(), "questionId");
            Map<String, Object> question = db.one(
                    """
                    select id,correct_answer,score
                    from questions
                    where id=? and ai_session_id=? and is_active=true
                    """,
                    questionId,
                    sessionId
            );

            String correctAnswer = String.valueOf(question.get("correct_answer"));
            boolean isCorrect = correctAnswer.trim().equalsIgnoreCase(answerItem.userAnswer().trim());
            int score = isCorrect ? ApiDataMapper.asInt(question.get("score")) : 0;
            if (isCorrect) {
                correctCount++;
            }
            totalScore += score;

            db.update(
                    """
                    insert into ai_question_record_items(id,record_id,question_id,user_answer,correct_answer,is_correct,score,analysis)
                    values (?,?,?,?,?,?,?,(select analysis from questions where id=?))
                    """,
                    db.newId(),
                    recordId,
                    questionId,
                    answerItem.userAnswer(),
                    correctAnswer,
                    isCorrect,
                    score,
                    questionId
            );

            items.add(Map.of(
                    "questionId", questionId.toString(),
                    "userAnswer", answerItem.userAnswer(),
                    "correctAnswer", correctAnswer,
                    "isCorrect", isCorrect,
                    "score", score
            ));
        }

        db.update("update ai_question_records set correct_count=?,total_score=? where id=?", correctCount, totalScore, recordId);
        BigDecimal rate = req.answers().isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf((double) correctCount * 100D / (double) req.answers().size()).setScale(2, RoundingMode.HALF_UP);
        db.update(
                "update ai_question_sessions set completed=true,correct_rate=?,score=?,updated_at=now() where id=?",
                rate,
                totalScore,
                sessionId
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordId", recordId.toString());
        data.put("sessionId", sessionId.toString());
        data.put("totalQuestions", req.answers().size());
        data.put("correctCount", correctCount);
        data.put("totalScore", totalScore);
        data.put("items", items);

        governance.storeIdempotency("student.aiq.submit", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(user.userId(), user.role(), "SUBMIT_AIQ", "AIQ_RECORD", recordId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/ai-questions/{recordId}/analysis")
    public ResponseEntity<ApiResponse> aiQuestionAnalysis(@PathVariable("recordId") UUID recordId, HttpServletRequest request) {
        requireRole("STUDENT");
        ensureAiRecordOwner(recordId);
        AuthUser user = currentUser();

        List<Map<String, Object>> rows = db.list(
                """
                select
                  i.question_id,
                  q.content,
                  i.user_answer,
                  i.correct_answer,
                  i.is_correct,
                  coalesce(i.analysis, q.analysis) as analysis,
                  q.knowledge_points,
                  (
                    select s.suggestion
                    from teacher_suggestions s
                    where s.student_id=?
                      and (
                        s.question_id=i.question_id
                        or (
                          s.knowledge_point is not null
                          and q.knowledge_points is not null
                          and jsonb_exists(q.knowledge_points, s.knowledge_point)
                        )
                      )
                    order by s.created_at desc
                    limit 1
                  ) as teacher_suggestion
                from ai_question_record_items i
                join questions q on q.id=i.question_id
                where i.record_id=?
                order by i.created_at asc
                """,
                user.userId(),
                recordId
        );

        List<Map<String, Object>> items = rows.stream().map(this::toExerciseAnalysisItemVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "recordId", recordId.toString(),
                "items", items
        ), trace(request)));
    }

    private void ensureSessionOwner(UUID sessionId) {
        Map<String, Object> session = db.one(
                "select student_id,is_deleted from chat_sessions where id=?",
                sessionId
        );
        if (ApiDataMapper.asBoolean(session.get("is_deleted"))) {
            throw new ResourceNotFoundException("资源不存在");
        }
        if (!currentUser().userId().equals(session.get("student_id"))) {
            throw new SecurityException("非资源归属者");
        }
    }

    private void ensureExerciseRecordOwner(UUID recordId) {
        Map<String, Object> record = db.one("select student_id from exercise_records where id=?", recordId);
        if (!currentUser().userId().equals(record.get("student_id"))) {
            throw new SecurityException("非资源归属者");
        }
    }

    private void ensureAiSessionOwner(UUID sessionId) {
        Map<String, Object> session = db.one("select student_id from ai_question_sessions where id=?", sessionId);
        if (!currentUser().userId().equals(session.get("student_id"))) {
            throw new SecurityException("非资源归属者");
        }
    }

    private void ensureAiRecordOwner(UUID recordId) {
        Map<String, Object> record = db.one("select student_id from ai_question_records where id=?", recordId);
        if (!currentUser().userId().equals(record.get("student_id"))) {
            throw new SecurityException("非资源归属者");
        }
    }

    private void updateSessionTitleIfNeeded(UUID sessionId) {
        Map<String, Object> first = db.oneOrNull(
                "select content from chat_messages where session_id=? and role='USER' order by created_at asc limit 1",
                sessionId
        );
        if (first == null) {
            return;
        }
        String title = String.valueOf(first.get("content"));
        title = title.length() > 20 ? title.substring(0, 20) : title;
        db.update("update chat_sessions set title=?,updated_at=now() where id=? and title='新建对话'", title, sessionId);
    }

    private void upsertWrongBook(UUID studentId, UUID questionId) {
        if (db.exists("select 1 from wrong_book where student_id=? and question_id=?", studentId, questionId)) {
            db.update(
                    """
                    update wrong_book
                    set wrong_count=wrong_count+1,last_wrong_time=now(),status='ACTIVE',updated_at=now()
                    where student_id=? and question_id=?
                    """,
                    studentId,
                    questionId
            );
            return;
        }
        db.update(
                "insert into wrong_book(id,student_id,question_id,wrong_count,status) values (?,?,?,1,'ACTIVE')",
                db.newId(),
                studentId,
                questionId
        );
    }

    private String fetchSuggestionByQuestion(UUID studentId, UUID questionId) {
        Map<String, Object> row = db.oneOrNull(
                """
                select suggestion
                from teacher_suggestions
                where student_id=? and question_id=?
                order by created_at desc
                limit 1
                """,
                studentId,
                questionId
        );
        return row == null ? null : ApiDataMapper.asString(row.get("suggestion"));
    }

    private int parseTokenUsage(Object tokenUsagePayload) {
        if (tokenUsagePayload == null) {
            return 0;
        }
        try {
            Map<String, Object> map = objectMapper.convertValue(tokenUsagePayload, new TypeReference<>() {
            });
            return ApiDataMapper.asInt(map.get("prompt")) + ApiDataMapper.asInt(map.get("completion"));
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException(fieldName + " 必须是合法 UUID");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "null";
        }
    }

    private Map<String, Object> toChatSessionVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("studentId", String.valueOf(row.get("student_id")));
        out.put("title", String.valueOf(row.get("title")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        out.put("updatedAt", ApiDataMapper.asIsoTime(row.get("updated_at")));
        return out;
    }

    private Map<String, Object> toChatMessageVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> citations = ApiDataMapper.parseObjectList(row.get("citations"), objectMapper);
        out.put("id", String.valueOf(row.get("id")));
        out.put("role", String.valueOf(row.get("role")));
        out.put("content", String.valueOf(row.get("content")));
        out.put("citations", citations.isEmpty() ? null : citations);
        out.put("tokenUsage", ApiDataMapper.asInt(row.get("token_usage")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        return out;
    }

    private Map<String, Object> toQuestionVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("subject", String.valueOf(row.get("subject")));
        out.put("questionType", String.valueOf(row.get("question_type")));
        out.put("difficulty", String.valueOf(row.get("difficulty")));
        out.put("content", String.valueOf(row.get("content")));
        out.put("options", ApiDataMapper.parseNullableStringMap(row.get("options"), objectMapper));
        out.put("correctAnswer", ApiDataMapper.asString(row.get("correct_answer")));
        out.put("analysis", ApiDataMapper.asString(row.get("analysis")));
        out.put("knowledgePoints", ApiDataMapper.parseNullableStringList(row.get("knowledge_points"), objectMapper));
        out.put("score", ApiDataMapper.asInt(row.get("score")));
        out.put("source", String.valueOf(row.get("source")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        return out;
    }

    private Map<String, Object> toExerciseAnalysisItemVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questionId", String.valueOf(row.get("question_id")));
        out.put("content", String.valueOf(row.get("content")));
        out.put("userAnswer", ApiDataMapper.asString(row.get("user_answer")));
        out.put("correctAnswer", ApiDataMapper.asString(row.get("correct_answer")));
        out.put("isCorrect", ApiDataMapper.asBoolean(row.get("is_correct")));
        out.put("analysis", ApiDataMapper.asString(row.get("analysis")));
        out.put("knowledgePoints", ApiDataMapper.parseNullableStringList(row.get("knowledge_points"), objectMapper));
        out.put("teacherSuggestion", ApiDataMapper.asString(row.get("teacher_suggestion")));
        return out;
    }

    private Map<String, Object> toWrongBookEntryVo(Map<String, Object> row) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", String.valueOf(row.get("question_id")));
        question.put("subject", String.valueOf(row.get("subject")));
        question.put("questionType", String.valueOf(row.get("question_type")));
        question.put("difficulty", String.valueOf(row.get("difficulty")));
        question.put("content", String.valueOf(row.get("content")));
        question.put("options", ApiDataMapper.parseNullableStringMap(row.get("options"), objectMapper));
        question.put("correctAnswer", ApiDataMapper.asString(row.get("correct_answer")));
        question.put("analysis", ApiDataMapper.asString(row.get("analysis")));
        question.put("knowledgePoints", ApiDataMapper.parseNullableStringList(row.get("knowledge_points"), objectMapper));
        question.put("score", ApiDataMapper.asInt(row.get("score")));
        question.put("source", String.valueOf(row.get("source")));
        question.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("questionId", String.valueOf(row.get("question_id")));
        out.put("question", question);
        out.put("wrongCount", ApiDataMapper.asInt(row.get("wrong_count")));
        out.put("lastWrongTime", ApiDataMapper.asIsoTime(row.get("last_wrong_time")));
        out.put("status", String.valueOf(row.get("status")));
        return out;
    }

    private Map<String, Object> toExerciseRecordVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("subject", ApiDataMapper.asString(row.get("subject")));
        out.put("totalQuestions", ApiDataMapper.asInt(row.get("total_questions")));
        out.put("correctCount", ApiDataMapper.asInt(row.get("correct_count")));
        out.put("totalScore", ApiDataMapper.asInt(row.get("total_score")));
        out.put("timeSpent", ApiDataMapper.asInt(row.get("time_spent")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        return out;
    }

    private Map<String, Object> toAiSessionVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("subject", String.valueOf(row.get("subject")));
        out.put("difficulty", ApiDataMapper.asString(row.get("difficulty")));
        out.put("questionCount", ApiDataMapper.asInt(row.get("question_count")));
        out.put("completed", ApiDataMapper.asBoolean(row.get("completed")));
        out.put("correctRate", row.get("correct_rate") == null ? null : ApiDataMapper.asDouble(row.get("correct_rate")));
        out.put("score", row.get("score") == null ? null : ApiDataMapper.asInt(row.get("score")));
        out.put("generatedAt", ApiDataMapper.asIsoTime(row.get("generated_at")));
        return out;
    }

    public record ChatSendReq(@NotBlank @Size(min = 1, max = 4000) String message) {
    }

    public record ExerciseSubmitReq(
            @NotNull @Size(min = 1) List<@Valid AnswerItem> answers,
            Integer timeSpent
    ) {
    }

    public record AiGenerateReq(
            @Min(1) @Max(20) int count,
            @NotBlank String subject,
            @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
            List<String> conceptTags
    ) {
    }

    public record AiSubmitReq(
            @NotBlank String sessionId,
            @NotNull @Size(min = 1) List<@Valid AnswerItem> answers
    ) {
    }

    public record AnswerItem(@NotBlank String questionId, @NotBlank String userAnswer) {
    }
}
