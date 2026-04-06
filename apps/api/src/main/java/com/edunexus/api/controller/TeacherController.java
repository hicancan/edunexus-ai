package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.service.AiClient;
import com.edunexus.api.service.AnalyticsService;
import com.edunexus.api.service.GovernanceService;
import com.edunexus.api.service.KnowledgeService;
import com.edunexus.api.service.LessonPlanService;
import com.edunexus.api.service.SuggestionService;
import com.edunexus.api.service.VoMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController implements ControllerSupport {

    private final AiClient aiClient;
    private final AnalyticsService analyticsService;
    private final KnowledgeService knowledgeService;
    private final LessonPlanService lessonPlanService;
    private final SuggestionService suggestionService;
    private final GovernanceService governance;
    private final VoMapper voMapper;

    public TeacherController(
            AiClient aiClient,
            AnalyticsService analyticsService,
            KnowledgeService knowledgeService,
            LessonPlanService lessonPlanService,
            SuggestionService suggestionService,
            GovernanceService governance,
            VoMapper voMapper) {
        this.aiClient = aiClient;
        this.analyticsService = analyticsService;
        this.knowledgeService = knowledgeService;
        this.lessonPlanService = lessonPlanService;
        this.suggestionService = suggestionService;
        this.governance = governance;
        this.voMapper = voMapper;
    }

    // ── Classrooms & Students ────────────────────────────────────────────────

    @GetMapping("/classrooms")
    public ResponseEntity<ApiResponse> listClassrooms(HttpServletRequest request) {
        requireRole("TEACHER");
        var classrooms = knowledgeService.listClassrooms(currentUser().userId());
        var data = classrooms.stream().map(voMapper::toClassroomVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/students")
    public ResponseEntity<ApiResponse> listStudents(HttpServletRequest request) {
        requireRole("TEACHER");
        var students = analyticsService.listStudents(currentUser().userId());
        var data = students.stream().map(voMapper::toTeacherStudentVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    // ── Analytics & Attribution ──────────────────────────────────────────────

    @GetMapping("/students/{studentId}/analytics")
    public ResponseEntity<ApiResponse> studentAnalytics(
            @PathVariable("studentId") UUID studentId, HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        analyticsService.ensureStudentLinked(user.userId(), studentId);
        var data = analyticsService.getStudentAnalytics(studentId);
        governance.audit(
                user.userId(),
                user.role(),
                "VIEW_STUDENT_ANALYTICS",
                "STUDENT_ANALYTICS",
                studentId.toString(),
                trace(request),
                Map.of(
                        "executionLane",
                        "HUMAN_IN_LOOP",
                        "supportStage",
                        supportStageLabel(data),
                        "activeWrongCount",
                        ApiDataMapper.asInt(data.get("wrongBookCount")),
                        "interactionProfile",
                        ApiDataMapper.asString(data.get("interactionProfile"))));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/students/{studentId}/attribution")
    public ResponseEntity<ApiResponse> studentAttribution(
            @PathVariable("studentId") UUID studentId, HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        analyticsService.ensureStudentLinked(user.userId(), studentId);
        var data = analyticsService.getStudentAttribution(studentId);
        governance.audit(
                user.userId(),
                user.role(),
                "VIEW_STUDENT_ATTRIBUTION",
                "STUDENT_ATTRIBUTION",
                studentId.toString(),
                trace(request),
                Map.of(
                        "executionLane",
                        "HUMAN_IN_LOOP",
                        "knowledgePoint",
                        ApiDataMapper.asString(data.get("knowledgePoint")),
                        "impactCount",
                        ApiDataMapper.asInt(data.get("impactCount"))));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/interventions/recommendations")
    public ResponseEntity<ApiResponse> interventionRecommendations(HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        var data = analyticsService.getInterventionRecommendations(user.userId());
        governance.audit(
                user.userId(),
                user.role(),
                "VIEW_INTERVENTION_RECOMMENDATIONS",
                "TEACHER_SUGGESTION",
                "classroom",
                trace(request),
                Map.of(
                        "executionLane",
                        data.isEmpty()
                                ? "HUMAN_IN_LOOP"
                                : executionLaneFromProvider(
                                        ApiDataMapper.asString(data.getFirst().get("provider"))),
                        "recommendationCount",
                        data.size(),
                        "provider",
                        data.isEmpty() ? "" : ApiDataMapper.asString(data.getFirst().get("provider")),
                        "model",
                        data.isEmpty() ? "" : ApiDataMapper.asString(data.getFirst().get("model")),
                        "latencyMs",
                        data.isEmpty() ? 0 : ApiDataMapper.asInt(data.getFirst().get("latencyMs")),
                        "routerDecision",
                        data.isEmpty()
                                ? ""
                                : ApiDataMapper.asString(data.getFirst().get("routerDecision"))));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    // ── Suggestions ──────────────────────────────────────────────────────────

    @PostMapping("/suggestions")
    public ResponseEntity<ApiResponse> createSuggestion(
            @Valid @RequestBody SuggestionReq req, HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        if ((req.questionId() == null || req.questionId().isBlank())
                && (req.knowledgePoint() == null || req.knowledgePoint().isBlank()))
            throw new IllegalArgumentException("questionId 与 knowledgePoint 至少填写一个");

        UUID studentId = UUID.fromString(req.studentId());
        UUID questionId =
                (req.questionId() != null && !req.questionId().isBlank())
                        ? UUID.fromString(req.questionId())
                        : null;
        var saveResult =
                suggestionService.save(
                        user.userId(),
                        studentId,
                        questionId,
                        req.knowledgePoint(),
                        req.suggestion());
        var suggestion = saveResult.suggestion();
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("executionLane", "HUMAN_IN_LOOP");
        auditDetail.put("studentId", req.studentId());
        if (req.questionId() != null && !req.questionId().isBlank()) {
            auditDetail.put("questionId", req.questionId());
        }
        if (req.knowledgePoint() != null && !req.knowledgePoint().isBlank()) {
            auditDetail.put("knowledgePoint", req.knowledgePoint());
        }
        auditDetail.put("suggestionLength", req.suggestion().length());
        auditDetail.put("created", saveResult.created());
        governance.audit(
                user.userId(),
                user.role(),
                saveResult.created() ? "CREATE_SUGGESTION" : "UPDATE_SUGGESTION",
                "TEACHER_SUGGESTION",
                suggestion.id().toString(),
                trace(request),
                auditDetail);
        return ResponseEntity.ok(
                ApiResponse.ok(voMapper.toTeacherSuggestionVo(suggestion), trace(request)));
    }

    @PostMapping("/suggestions/bulk")
    @Transactional
    public ResponseEntity<ApiResponse> dispatchSuggestionBulk(
            @Valid @RequestBody BulkSuggestionReq req, HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        var result =
                suggestionService.createBulk(
                        user.userId(), req.knowledgePoint().trim(), req.suggestion());
        governance.audit(
                user.userId(),
                user.role(),
                "BULK_CREATE_SUGGESTION",
                "TEACHER_SUGGESTION",
                req.knowledgePoint(),
                trace(request),
                Map.of(
                        "executionLane",
                        "HUMAN_IN_LOOP",
                        "knowledgePoint",
                        result.knowledgePoint(),
                        "affectedCount",
                        result.affectedCount(),
                        "createdCount",
                        result.createdCount(),
                        "updatedCount",
                        result.updatedCount(),
                        "targetStudentCount",
                        result.studentIds().size()));
        return ResponseEntity.ok(
                ApiResponse.ok(
                        Map.of(
                                "knowledgePoint",
                                result.knowledgePoint(),
                                "affectedCount",
                                result.affectedCount(),
                                "createdCount",
                                result.createdCount(),
                                "updatedCount",
                                result.updatedCount(),
                                "studentIds",
                                result.studentIds()),
                        trace(request)));
    }

    // ── Knowledge Base ───────────────────────────────────────────────────────

    @PostMapping("/knowledge/documents")
    public ResponseEntity<ApiResponse> uploadDocument(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam("classId") UUID classId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request)
            throws IOException {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        if (file.isEmpty()) throw new IllegalArgumentException("文件不能为空");

        String filename =
                file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String lowered = filename.toLowerCase(Locale.ROOT);
        if (!(lowered.endsWith(".pdf")
                || lowered.endsWith(".docx")
                || lowered.endsWith(".txt")
                || lowered.endsWith(".md")))
            throw new IllegalArgumentException("仅支持 PDF/Docx/TXT/MD 文件");

        byte[] fileBytes = file.getBytes();
        String requestHash =
                governance.requestHash(
                        Map.of(
                                "teacherId",
                                user.userId(),
                                "classId",
                                classId,
                                "filename",
                                filename,
                                "fileType",
                                file.getContentType(),
                                "fileSize",
                                file.getSize(),
                                "contentSha256",
                                KnowledgeService.computeHash(fileBytes)));
        Map<String, Object> replay =
                governance.getIdempotentReplay(
                        "teacher.knowledge.upload", idempotencyKey, requestHash);
        if (replay != null)
            return ResponseEntity.status(202).body(ApiResponse.accepted(replay, trace(request)));

        var result =
                knowledgeService.uploadDocument(
                        user.userId(),
                        classId,
                        filename,
                        file.getContentType() == null
                                ? "application/octet-stream"
                                : file.getContentType(),
                        file.getSize(),
                        fileBytes,
                        trace(request),
                        idempotencyKey);

        var data = voMapper.toDocumentVo(result.document());
        governance.storeIdempotency(
                "teacher.knowledge.upload",
                idempotencyKey,
                requestHash,
                data,
                java.time.Duration.ofHours(24));
        governance.audit(
                user.userId(),
                user.role(),
                "UPLOAD_DOCUMENT",
                "DOCUMENT",
                result.documentId().toString(),
                trace(request),
                Map.of(
                        "executionLane",
                        "HYBRID",
                        "filename",
                        filename,
                        "classId",
                        classId.toString(),
                        "className",
                        result.document().classroomName() == null
                                ? ""
                                : result.document().classroomName(),
                        "status",
                        result.document().status()));
        return ResponseEntity.status(202).body(ApiResponse.accepted(data, trace(request)));
    }

    @GetMapping("/knowledge/documents")
    public ResponseEntity<ApiResponse> listDocuments(
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request) {
        requireRole("TEACHER");
        if (status != null
                && !status.isBlank()
                && !"UPLOADING".equals(status)
                && !"PARSING".equals(status)
                && !"EMBEDDING".equals(status)
                && !"READY".equals(status)
                && !"FAILED".equals(status))
            throw new IllegalArgumentException(
                    "status 仅支持 UPLOADING/PARSING/EMBEDDING/READY/FAILED");

        var docs = knowledgeService.listDocuments(currentUser().userId(), status);
        var data = docs.stream().map(voMapper::toDocumentVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @DeleteMapping("/knowledge/documents/{documentId}")
    @Transactional
    public ResponseEntity<ApiResponse> deleteDocument(
            @PathVariable("documentId") UUID documentId, HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        knowledgeService.deleteDocument(documentId, user.userId(), trace(request));
        governance.audit(
                user.userId(),
                user.role(),
                "DELETE_DOCUMENT",
                "DOCUMENT",
                documentId.toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    // ── Lesson Plans ─────────────────────────────────────────────────────────

    @PostMapping("/plans/generate")
    @Transactional
    public ResponseEntity<ApiResponse> generatePlan(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlanGenerateReq req,
            HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        String requestHash =
                governance.requestHash(Map.of("teacherId", user.userId(), "payload", req));
        Map<String, Object> replay =
                governance.getIdempotentReplay(
                        "teacher.plan.generate", idempotencyKey, requestHash);
        if (replay != null) return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));

        var planResult =
                lessonPlanService.generateAndSave(
                        user.userId(),
                        req.topic(),
                        req.gradeLevel(),
                        req.durationMins(),
                        trace(request),
                        idempotencyKey);
        String executionLane = executionLaneFromProvider(planResult.provider());
        var data =
                voMapper.toLessonPlanVo(
                        planResult.plan(),
                        planResult.provider(),
                        planResult.model(),
                        planResult.latencyMs(),
                        executionLane);
        governance.storeIdempotency(
                "teacher.plan.generate",
                idempotencyKey,
                requestHash,
                data,
                java.time.Duration.ofHours(24));
        governance.audit(
                user.userId(),
                user.role(),
                "GENERATE_PLAN",
                "LESSON_PLAN",
                planResult.plan().id().toString(),
                trace(request),
                Map.of(
                        "executionLane",
                        executionLane,
                        "provider",
                        planResult.provider() == null ? "" : planResult.provider(),
                        "model",
                        planResult.model() == null ? "" : planResult.model(),
                        "latencyMs",
                        planResult.latencyMs(),
                        "topic",
                        req.topic(),
                        "durationMins",
                        req.durationMins()));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse> listPlans(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("TEACHER");
        var plans = lessonPlanService.list(currentUser().userId(), page, size);
        long total = lessonPlanService.count(currentUser().userId());
        var content = plans.stream().map(voMapper::toLessonPlanVo).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse> updatePlan(
            @PathVariable("planId") UUID planId,
            @Valid @RequestBody PlanUpdateReq req,
            HttpServletRequest request) {
        requireRole("TEACHER");
        var plan = lessonPlanService.update(planId, currentUser().userId(), req.contentMd());
        governance.audit(
                currentUser().userId(),
                currentUser().role(),
                "UPDATE_PLAN",
                "LESSON_PLAN",
                planId.toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(voMapper.toLessonPlanVo(plan), trace(request)));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse> deletePlan(
            @PathVariable("planId") UUID planId, HttpServletRequest request) {
        requireRole("TEACHER");
        lessonPlanService.delete(planId, currentUser().userId());
        governance.audit(
                currentUser().userId(),
                currentUser().role(),
                "DELETE_PLAN",
                "LESSON_PLAN",
                planId.toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @GetMapping("/plans/{planId}/export")
    public ResponseEntity<byte[]> exportPlan(
            @PathVariable("planId") UUID planId,
            @RequestParam("format") String format,
            HttpServletRequest request) {
        requireRole("TEACHER");
        if (!"md".equals(format) && !"pdf".equals(format))
            throw new IllegalArgumentException("format 仅支持 md/pdf");

        byte[] content = lessonPlanService.export(planId, currentUser().userId(), format);
        String topic = lessonPlanService.getTopicForExport(planId);
        MediaType contentType =
                "pdf".equals(format)
                        ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("text/markdown; charset=UTF-8");
        governance.audit(
                currentUser().userId(),
                currentUser().role(),
                "EXPORT_PLAN",
                "LESSON_PLAN",
                planId.toString(),
                trace(request));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + com.edunexus.api.common.FilenameUtil.sanitize(topic)
                                + "."
                                + format
                                + "\"")
                .header("X-Request-Id", trace(request))
                .contentType(contentType)
                .body(content);
    }

    @PostMapping("/plans/{planId}/share")
    public ResponseEntity<ApiResponse> sharePlan(
            @PathVariable("planId") UUID planId, HttpServletRequest request) {
        requireRole("TEACHER");
        var result = lessonPlanService.share(planId, currentUser().userId());
        governance.audit(
                currentUser().userId(),
                currentUser().role(),
                "SHARE_PLAN",
                "LESSON_PLAN",
                planId.toString(),
                trace(request));
        return ResponseEntity.ok(
                ApiResponse.ok(
                        Map.of(
                                "planId",
                                result.planId().toString(),
                                "shareToken",
                                result.shareToken(),
                                "shareUrl",
                                "/api/v1/teacher/plans/shared/" + result.shareToken()),
                        trace(request)));
    }

    @GetMapping("/plans/shared/{shareToken}")
    public ResponseEntity<ApiResponse> getSharedPlan(
            @PathVariable("shareToken") String shareToken, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        voMapper.toLessonPlanVo(lessonPlanService.getShared(shareToken)),
                        trace(request)));
    }

    // ── Intervention Sandbox ─────────────────────────────────────────────────

    @PostMapping("/interventions/sandbox")
    public ResponseEntity<ApiResponse> interventionSandbox(
            @Valid @RequestBody InterventionSandboxReq req, HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        var recommendations = analyticsService.getInterventionRecommendations(user.userId());

        List<Map<String, Object>> clusters = recommendations.stream()
                .map(r -> {
                    Map<String, Object> cluster = new LinkedHashMap<>();
                    cluster.put("knowledgePoint", ApiDataMapper.asString(r.get("knowledgePoint")));
                    cluster.put("studentCount", ApiDataMapper.asInt(r.get("studentCount")));
                    cluster.put("totalWrongCount", ApiDataMapper.asInt(r.get("totalWrongCount")));
                    return cluster;
                })
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traceId", trace(request));
        body.put("classWrongClusters", clusters);
        body.put("studentCount", req.studentCount());

        var result = aiClient.interventionSandbox(body);
        governance.audit(
                user.userId(),
                user.role(),
                "INTERVENTION_SANDBOX",
                "TEACHER_SANDBOX",
                "classroom",
                trace(request),
                Map.of(
                        "executionLane",
                        executionLaneFromProvider(ApiDataMapper.asString(result.get("provider"))),
                        "provider",
                        ApiDataMapper.asString(result.get("provider")),
                        "model",
                        ApiDataMapper.asString(result.get("model")),
                        "latencyMs",
                        ApiDataMapper.asInt(result.get("latencyMs")),
                        "strategyCount",
                        result.get("data") instanceof List<?> l ? l.size() : 0));
        return ResponseEntity.ok(ApiResponse.ok(result, trace(request)));
    }

    // ── Request records ──────────────────────────────────────────────────────

    public record PlanGenerateReq(
            @NotBlank String topic,
            @NotBlank String gradeLevel,
            @Min(10) @Max(180) int durationMins) {}

    public record PlanUpdateReq(@NotBlank String contentMd) {}

    public record SuggestionReq(
            @NotBlank String studentId,
            String questionId,
            String knowledgePoint,
            @NotBlank @Size(min = 1, max = 2000) String suggestion) {}

    public record BulkSuggestionReq(
            @NotBlank @Size(min = 1, max = 100) String knowledgePoint,
            @NotBlank @Size(min = 1, max = 2000) String suggestion) {}

    public record InterventionSandboxReq(@Min(1) @Max(200) int studentCount) {}

    private String executionLaneFromProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "HYBRID";
        }
        return "ollama".equalsIgnoreCase(provider) ? "EDGE" : "CLOUD";
    }

    private String supportStageLabel(Map<String, Object> analytics) {
        Object stageObject = analytics.get("supportStage");
        if (!(stageObject instanceof Map<?, ?> stage)) {
            return "";
        }
        Object label = stage.get("label");
        return label == null ? "" : String.valueOf(label);
    }
}
