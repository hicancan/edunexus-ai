package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ApiResponse;
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

    private final AnalyticsService analyticsService;
    private final KnowledgeService knowledgeService;
    private final LessonPlanService lessonPlanService;
    private final SuggestionService suggestionService;
    private final GovernanceService governance;
    private final VoMapper voMapper;

    public TeacherController(
            AnalyticsService analyticsService,
            KnowledgeService knowledgeService,
            LessonPlanService lessonPlanService,
            SuggestionService suggestionService,
            GovernanceService governance,
            VoMapper voMapper) {
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
        analyticsService.ensureStudentLinked(currentUser().userId(), studentId);
        return ResponseEntity.ok(
                ApiResponse.ok(analyticsService.getStudentAnalytics(studentId), trace(request)));
    }

    @GetMapping("/students/{studentId}/attribution")
    public ResponseEntity<ApiResponse> studentAttribution(
            @PathVariable("studentId") UUID studentId, HttpServletRequest request) {
        requireRole("TEACHER");
        analyticsService.ensureStudentLinked(currentUser().userId(), studentId);
        return ResponseEntity.ok(
                ApiResponse.ok(analyticsService.getStudentAttribution(studentId), trace(request)));
    }

    @GetMapping("/interventions/recommendations")
    public ResponseEntity<ApiResponse> interventionRecommendations(HttpServletRequest request) {
        requireRole("TEACHER");
        var data = analyticsService.getInterventionRecommendations(currentUser().userId());
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
        var suggestion =
                suggestionService.create(
                        user.userId(),
                        studentId,
                        questionId,
                        req.knowledgePoint(),
                        req.suggestion());
        governance.audit(
                user.userId(),
                user.role(),
                "CREATE_SUGGESTION",
                "TEACHER_SUGGESTION",
                suggestion.id().toString(),
                trace(request));
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
                trace(request));
        return ResponseEntity.ok(
                ApiResponse.ok(
                        Map.of(
                                "knowledgePoint",
                                result.knowledgePoint(),
                                "createdCount",
                                result.createdCount(),
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
                trace(request));
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

        var plan =
                lessonPlanService.generateAndSave(
                        user.userId(),
                        req.topic(),
                        req.gradeLevel(),
                        req.durationMins(),
                        trace(request),
                        idempotencyKey);
        var data = voMapper.toLessonPlanVo(plan);
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
                plan.id().toString(),
                trace(request));
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
}
