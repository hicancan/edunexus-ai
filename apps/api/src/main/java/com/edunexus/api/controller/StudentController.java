package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.common.Difficulty;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.service.AiQuestionService;
import com.edunexus.api.service.ChatService;
import com.edunexus.api.service.ExerciseService;
import com.edunexus.api.service.GovernanceService;
import com.edunexus.api.service.VoMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@Validated
@RestController
@RequestMapping("/api/v1/student")
public class StudentController implements ControllerSupport {

    private final ChatService chatService;
    private final ExerciseService exerciseService;
    private final AiQuestionService aiQuestionService;
    private final GovernanceService governance;
    private final VoMapper voMapper;

    public StudentController(
            ChatService chatService,
            ExerciseService exerciseService,
            AiQuestionService aiQuestionService,
            GovernanceService governance,
            VoMapper voMapper) {
        this.chatService = chatService;
        this.exerciseService = exerciseService;
        this.aiQuestionService = aiQuestionService;
        this.governance = governance;
        this.voMapper = voMapper;
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    @PostMapping("/chat/session")
    public ResponseEntity<ApiResponse> createSession(HttpServletRequest request) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        var session = chatService.createSession(user.userId());
        governance.audit(
                user.userId(),
                user.role(),
                "CREATE_CHAT_SESSION",
                "CHAT_SESSION",
                session.id().toString(),
                trace(request));
        return ResponseEntity.status(201)
                .body(ApiResponse.created(voMapper.toChatSessionVo(session), trace(request)));
    }

    @GetMapping("/chat/sessions")
    public ResponseEntity<ApiResponse> listSessions(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("STUDENT");
        var sessions = chatService.listSessions(currentUser().userId(), page, size);
        long total = chatService.countSessions(currentUser().userId());
        var content = sessions.stream().map(voMapper::toChatSessionVo).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse> getSession(
            @PathVariable("sessionId") UUID sessionId, HttpServletRequest request) {
        requireRole("STUDENT");
        chatService.ensureSessionOwner(sessionId, currentUser().userId());
        return ResponseEntity.ok(
                ApiResponse.ok(chatService.getSessionDetail(sessionId), trace(request)));
    }

    @DeleteMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse> deleteSession(
            @PathVariable("sessionId") UUID sessionId, HttpServletRequest request) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        chatService.ensureSessionOwner(sessionId, user.userId());
        chatService.deleteSession(sessionId);
        governance.audit(
                user.userId(),
                user.role(),
                "DELETE_CHAT_SESSION",
                "CHAT_SESSION",
                sessionId.toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @PostMapping("/chat/session/{sessionId}/message")
    @Transactional
    public Object sendMessage(
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody ChatSendReq req,
            HttpServletRequest request) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        chatService.ensureSessionOwner(sessionId, user.userId());

        if (wantsSse(request)) {
            return chatService.streamMessage(
                    sessionId, user.userId(), req.message(), trace(request));
        }
        var data = chatService.sendMessage(sessionId, user.userId(), req.message(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    // ── Exercise ─────────────────────────────────────────────────────────────

    @GetMapping("/exercise/questions")
    public ResponseEntity<ApiResponse> listQuestions(
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("STUDENT");
        Difficulty parsedDifficulty = Difficulty.fromString(difficulty);
        String difficultyParam = parsedDifficulty == null ? null : parsedDifficulty.name();

        var questions = exerciseService.listQuestions(subject, difficultyParam, page, size);
        long total = exerciseService.countQuestions(subject, difficultyParam);
        var content = questions.stream().map(voMapper::toQuestionVoForStudent).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PostMapping("/exercise/submit")
    @Transactional
    public ResponseEntity<ApiResponse> submitExercise(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExerciseSubmitReq req,
            HttpServletRequest request) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        String requestHash =
                governance.requestHash(Map.of("studentId", user.userId(), "payload", req));
        Map<String, Object> replay =
                governance.getIdempotentReplay(
                        "student.exercise.submit", idempotencyKey, requestHash);
        if (replay != null) return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));

        var answers =
                req.answers().stream()
                        .map(
                                a ->
                                        new ExerciseService.AnswerItem(
                                                parseUuid(a.questionId(), "questionId"),
                                                a.userAnswer()))
                        .toList();
        var result = exerciseService.submitExercise(user.userId(), answers, req.timeSpent());

        var data =
                Map.of(
                        "recordId", result.recordId().toString(),
                        "totalQuestions", result.totalQuestions(),
                        "correctCount", result.correctCount(),
                        "totalScore", result.totalScore(),
                        "items", result.items());
        governance.storeIdempotency(
                "student.exercise.submit", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(
                user.userId(),
                user.role(),
                "SUBMIT_EXERCISE",
                "EXERCISE_RECORD",
                result.recordId().toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/exercise/{recordId}/analysis")
    public ResponseEntity<ApiResponse> exerciseAnalysis(
            @PathVariable("recordId") UUID recordId, HttpServletRequest request) {
        requireRole("STUDENT");
        exerciseService.ensureRecordOwner(recordId, currentUser().userId());
        var items = exerciseService.getAnalysisItems(recordId, currentUser().userId());
        return ResponseEntity.ok(
                ApiResponse.ok(
                        Map.of("recordId", recordId.toString(), "items", items), trace(request)));
    }

    @GetMapping("/exercise/wrong-questions")
    public ResponseEntity<ApiResponse> wrongQuestions(
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("STUDENT");
        if (!"ACTIVE".equals(status) && !"MASTERED".equals(status))
            throw new IllegalArgumentException("status 仅支持 ACTIVE/MASTERED");

        var entries =
                exerciseService.listWrongQuestions(
                        currentUser().userId(), status, subject, page, size);
        long total = exerciseService.countWrongQuestions(currentUser().userId(), status, subject);
        var content =
                entries.stream()
                        .map(
                                w -> {
                                    var q = exerciseService.findQuestion(w.questionId());
                                    return voMapper.toWrongBookEntryVo(w, q);
                                })
                        .toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @DeleteMapping("/exercise/wrong-questions/{questionId}")
    public ResponseEntity<ApiResponse> removeWrongQuestion(
            @PathVariable("questionId") UUID questionId, HttpServletRequest request) {
        requireRole("STUDENT");
        int updated = exerciseService.markWrongQuestionMastered(currentUser().userId(), questionId);
        if (updated == 0) throw new ResourceNotFoundException("资源不存在");
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @GetMapping("/exercise/records")
    public ResponseEntity<ApiResponse> records(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("STUDENT");
        if (startDate != null && !startDate.isBlank()) LocalDate.parse(startDate);
        if (endDate != null && !endDate.isBlank()) LocalDate.parse(endDate);

        var recs =
                exerciseService.listRecords(currentUser().userId(), startDate, endDate, page, size);
        long total = exerciseService.countRecords(currentUser().userId(), startDate, endDate);
        var content = recs.stream().map(voMapper::toExerciseRecordVo).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    @GetMapping("/profile/weak-points")
    public ResponseEntity<ApiResponse> profileWeakPoints(HttpServletRequest request) {
        requireRole("STUDENT");
        var weakPoints = exerciseService.getWeakPoints(currentUser().userId());
        var data = weakPoints.stream().map(voMapper::toWeakPointVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    // ── AI Questions ─────────────────────────────────────────────────────────

    @PostMapping("/ai-questions/generate")
    @Transactional
    public ResponseEntity<ApiResponse> generateAiQuestions(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AiGenerateReq req,
            HttpServletRequest request) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        Difficulty parsedDifficulty = Difficulty.fromString(req.difficulty());
        String difficulty =
                parsedDifficulty == null ? Difficulty.MEDIUM.name() : parsedDifficulty.name();

        String requestHash =
                governance.requestHash(Map.of("studentId", user.userId(), "payload", req));
        Map<String, Object> replay =
                governance.getIdempotentReplay("student.aiq.generate", idempotencyKey, requestHash);
        if (replay != null) return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));

        var result =
                aiQuestionService.generateQuestions(
                        user.userId(),
                        req.count(),
                        req.subject(),
                        difficulty,
                        req.conceptTags(),
                        trace(request),
                        idempotencyKey);

        var data =
                Map.of("sessionId", result.sessionId().toString(), "questions", result.questions());
        governance.storeIdempotency(
                "student.aiq.generate", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(
                user.userId(),
                user.role(),
                "GENERATE_AIQ",
                "AIQ_SESSION",
                result.sessionId().toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/ai-questions")
    public ResponseEntity<ApiResponse> listAiSessions(
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("STUDENT");
        var sessions = aiQuestionService.listSessions(currentUser().userId(), subject, page, size);
        long total = aiQuestionService.countSessions(currentUser().userId(), subject);
        var content = sessions.stream().map(voMapper::toAiSessionVo).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PostMapping("/ai-questions/submit")
    @Transactional
    public ResponseEntity<ApiResponse> submitAiQuestions(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AiSubmitReq req,
            HttpServletRequest request) {
        requireRole("STUDENT");
        AuthUser user = currentUser();
        UUID sessionId = parseUuid(req.sessionId(), "sessionId");
        aiQuestionService.ensureSessionOwner(sessionId, user.userId());

        String requestHash =
                governance.requestHash(Map.of("studentId", user.userId(), "payload", req));
        Map<String, Object> replay =
                governance.getIdempotentReplay("student.aiq.submit", idempotencyKey, requestHash);
        if (replay != null) return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));

        var answers =
                req.answers().stream()
                        .map(
                                a ->
                                        new AiQuestionService.AnswerItem(
                                                parseUuid(a.questionId(), "questionId"),
                                                a.userAnswer()))
                        .toList();
        var result = aiQuestionService.submitQuestions(sessionId, user.userId(), answers);

        var data =
                Map.of(
                        "recordId", result.recordId().toString(),
                        "sessionId", result.sessionId().toString(),
                        "totalQuestions", result.totalQuestions(),
                        "correctCount", result.correctCount(),
                        "totalScore", result.totalScore(),
                        "items", result.items());
        governance.storeIdempotency(
                "student.aiq.submit", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(
                user.userId(),
                user.role(),
                "SUBMIT_AIQ",
                "AIQ_RECORD",
                result.recordId().toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/ai-questions/{recordId}/analysis")
    public ResponseEntity<ApiResponse> aiQuestionAnalysis(
            @PathVariable("recordId") UUID recordId, HttpServletRequest request) {
        requireRole("STUDENT");
        aiQuestionService.ensureRecordOwner(recordId, currentUser().userId());
        var items = aiQuestionService.getAnalysisItems(recordId, currentUser().userId());
        return ResponseEntity.ok(
                ApiResponse.ok(
                        Map.of("recordId", recordId.toString(), "items", items), trace(request)));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean wantsSse(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException(fieldName + " 必须是合法 UUID");
        }
    }

    // ── Request records ──────────────────────────────────────────────────────

    public record ChatSendReq(@NotBlank @Size(min = 1, max = 4000) String message) {}

    public record ExerciseSubmitReq(
            @NotNull @Size(min = 1) List<@Valid AnswerItem> answers, Integer timeSpent) {}

    public record AiGenerateReq(
            @Min(1) @Max(20) int count,
            @NotBlank String subject,
            @Pattern(regexp = "EASY|MEDIUM|HARD") String difficulty,
            List<String> conceptTags) {}

    public record AiSubmitReq(
            @NotBlank String sessionId, @NotNull @Size(min = 1) List<@Valid AnswerItem> answers) {}

    public record AnswerItem(@NotBlank String questionId, @NotBlank String userAnswer) {}
}
