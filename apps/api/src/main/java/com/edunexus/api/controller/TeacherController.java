package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.service.AiClient;
import com.edunexus.api.service.DbService;
import com.edunexus.api.service.GovernanceService;
import com.edunexus.api.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Validated
@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController implements ControllerSupport {
    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);

    private final DbService db;
    private final AiClient aiClient;
    private final ObjectStorageService objectStorageService;
    private final GovernanceService governance;
    private final TaskExecutor documentIngestExecutor;

    public TeacherController(
            DbService db,
            AiClient aiClient,
            ObjectStorageService objectStorageService,
            GovernanceService governance,
            @Qualifier("documentIngestExecutor") TaskExecutor documentIngestExecutor) {
        this.db = db;
        this.aiClient = aiClient;
        this.objectStorageService = objectStorageService;
        this.governance = governance;
        this.documentIngestExecutor = documentIngestExecutor;
    }

    @PostMapping("/knowledge/documents")
    public ResponseEntity<ApiResponse> uploadDocument(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) throws IOException {
        requireRole("TEACHER");
        AuthUser user = currentUser();

        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String filename = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String lowered = filename.toLowerCase(Locale.ROOT);
        if (!(lowered.endsWith(".pdf") || lowered.endsWith(".docx") || lowered.endsWith(".doc"))) {
            throw new IllegalArgumentException("仅支持 PDF/Docx 文件");
        }

        String fileType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        long fileSize = file.getSize();
        byte[] fileBytes = file.getBytes();
        String requestHash = governance.requestHash(Map.of(
                "teacherId", user.userId(),
                "filename", filename,
                "fileType", fileType,
                "fileSize", fileSize,
                "contentSha256", sha256(fileBytes)));

        Map<String, Object> replay = governance.getIdempotentReplay("teacher.knowledge.upload", idempotencyKey,
                requestHash);
        if (replay != null) {
            return ResponseEntity.status(202).body(ApiResponse.accepted(replay, trace(request)));
        }

        UUID documentId = db.newId();
        String storagePath = objectStorageService.upload(filename, fileType, fileBytes);
        db.update(
                """
                        insert into documents(id,teacher_id,filename,file_type,file_size,storage_path,status,error_message)
                        values (?,?,?,?,?,?,?,null)
                        """,
                documentId,
                user.userId(),
                filename,
                fileType,
                fileSize,
                storagePath,
                "UPLOADING");

        Map<String, Object> documentVo = db.one(
                "select id,filename,file_type,file_size,status,error_message,created_at,updated_at from documents where id=?",
                documentId);
        Map<String, Object> data = toDocumentVo(documentVo);
        governance.storeIdempotency("teacher.knowledge.upload", idempotencyKey, requestHash, data,
                Duration.ofHours(24));
        governance.audit(user.userId(), user.role(), "UPLOAD_DOCUMENT", "DOCUMENT", documentId.toString(),
                trace(request));

        UUID jobId = governance.createJobRun("DOCUMENT_INGEST", documentId, Map.of(
                "documentId", documentId.toString(),
                "teacherId", user.userId().toString(),
                "filename", filename,
                "storagePath", storagePath));

        Path tempFile = Files.createTempFile("edunexus-doc-" + documentId + "-", "-" + sanitizeFilename(filename));
        Files.write(tempFile, fileBytes);
        documentIngestExecutor.execute(() -> processDocumentInBackground(
                documentId,
                user.userId(),
                filename,
                tempFile,
                trace(request),
                idempotencyKey,
                jobId));

        return ResponseEntity.status(202).body(ApiResponse.accepted(data, trace(request)));
    }

    @GetMapping("/knowledge/documents")
    public ResponseEntity<ApiResponse> listDocuments(
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request) {
        requireRole("TEACHER");
        if (status != null && !status.isBlank()
                && !"UPLOADING".equals(status)
                && !"PARSING".equals(status)
                && !"EMBEDDING".equals(status)
                && !"READY".equals(status)
                && !"FAILED".equals(status)) {
            throw new IllegalArgumentException("status 仅支持 UPLOADING/PARSING/EMBEDDING/READY/FAILED");
        }
        AuthUser user = currentUser();

        StringBuilder sql = new StringBuilder(
                "select id,filename,file_type,file_size,status,error_message,created_at,updated_at from documents where teacher_id=? and deleted_at is null");
        List<Object> args = new ArrayList<>();
        args.add(user.userId());
        if (status != null && !status.isBlank()) {
            sql.append(" and status=?");
            args.add(status);
        }
        sql.append(" order by created_at desc");

        List<Map<String, Object>> rows = db.list(sql.toString(), args.toArray());
        List<Map<String, Object>> data = rows.stream().map(this::toDocumentVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @DeleteMapping("/knowledge/documents/{documentId}")
    @Transactional
    public ResponseEntity<ApiResponse> deleteDocument(@PathVariable("documentId") UUID documentId,
            HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();
        Map<String, Object> doc = ensureDocumentOwner(documentId);

        // M-06: 先标记删除，再异步删除向量 — 对齐 doc/05 §7.3
        objectStorageService.delete(String.valueOf(doc.get("storage_path")));
        db.update("update documents set deleted_at=now(),updated_at=now() where id=?", documentId);
        governance.audit(user.userId(), user.role(), "DELETE_DOCUMENT", "DOCUMENT", documentId.toString(),
                trace(request));

        String docIdStr = documentId.toString();
        String traceStr = trace(request);
        documentIngestExecutor.execute(() -> {
            try {
                aiClient.deleteKb(Map.of("traceId", traceStr, "documentId", docIdStr));
            } catch (Exception ex) {
                log.error("async_kb_delete_failed documentId={} traceId={}", docIdStr, traceStr, ex);
            }
        });

        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @PostMapping("/plans/generate")
    @Transactional
    public ResponseEntity<ApiResponse> generatePlan(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlanGenerateReq req,
            HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();

        String requestHash = governance.requestHash(Map.of("teacherId", user.userId(), "payload", req));
        Map<String, Object> replay = governance.getIdempotentReplay("teacher.plan.generate", idempotencyKey,
                requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }

        Map<String, Object> aiResp = aiClient.generatePlan(Map.of(
                "traceId", trace(request),
                "topic", req.topic(),
                "gradeLevel", req.gradeLevel(),
                "durationMins", req.durationMins(),
                "teacherId", user.userId().toString(),
                "idempotencyKey", idempotencyKey == null ? "" : idempotencyKey));

        String contentMd = String.valueOf(aiResp.getOrDefault("contentMd", "# 教案生成失败"));
        UUID planId = db.newId();
        db.update(
                "insert into lesson_plans(id,teacher_id,topic,grade_level,duration_mins,content_md) values (?,?,?,?,?,?)",
                planId,
                user.userId(),
                req.topic(),
                req.gradeLevel(),
                req.durationMins(),
                contentMd);

        Map<String, Object> plan = db.one(
                "select id,topic,grade_level,duration_mins,content_md,is_shared,share_token,shared_at,created_at,updated_at from lesson_plans where id=?",
                planId);
        Map<String, Object> data = toLessonPlanVo(plan);
        governance.storeIdempotency("teacher.plan.generate", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(user.userId(), user.role(), "GENERATE_PLAN", "LESSON_PLAN", planId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse> listPlans(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();

        int offset = (page - 1) * size;
        List<Map<String, Object>> rows = db.list(
                """
                        select id,topic,grade_level,duration_mins,content_md,is_shared,share_token,shared_at,created_at,updated_at
                        from lesson_plans
                        where teacher_id=? and deleted_at is null
                        order by updated_at desc
                        limit ? offset ?
                        """,
                user.userId(),
                size,
                offset);
        long total = db.count("select count(*) from lesson_plans where teacher_id=? and deleted_at is null",
                user.userId());
        List<Map<String, Object>> content = rows.stream().map(this::toLessonPlanVo).toList();

        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse> updatePlan(
            @PathVariable("planId") UUID planId,
            @Valid @RequestBody PlanUpdateReq req,
            HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);

        db.update("update lesson_plans set content_md=?,updated_at=now() where id=?", req.contentMd(), planId);
        Map<String, Object> row = db.one(
                "select id,topic,grade_level,duration_mins,content_md,is_shared,share_token,shared_at,created_at,updated_at from lesson_plans where id=?",
                planId);
        governance.audit(currentUser().userId(), currentUser().role(), "UPDATE_PLAN", "LESSON_PLAN", planId.toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(toLessonPlanVo(row), trace(request)));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse> deletePlan(@PathVariable("planId") UUID planId, HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);
        db.update("update lesson_plans set deleted_at=now(),updated_at=now() where id=?", planId);
        governance.audit(currentUser().userId(), currentUser().role(), "DELETE_PLAN", "LESSON_PLAN", planId.toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @GetMapping("/plans/{planId}/export")
    public ResponseEntity<byte[]> exportPlan(
            @PathVariable("planId") UUID planId,
            @RequestParam("format") String format,
            HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);
        if (!"md".equals(format) && !"pdf".equals(format)) {
            throw new IllegalArgumentException("format 仅支持 md/pdf");
        }

        Map<String, Object> plan = db.one("select topic,content_md from lesson_plans where id=? and deleted_at is null",
                planId);
        String topic = String.valueOf(plan.get("topic"));
        String contentMd = String.valueOf(plan.get("content_md"));

        byte[] content = "pdf".equals(format)
                ? renderPdf(topic, contentMd)
                : contentMd.getBytes(StandardCharsets.UTF_8);
        MediaType contentType = "pdf".equals(format)
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("text/markdown; charset=UTF-8");

        governance.audit(currentUser().userId(), currentUser().role(), "EXPORT_PLAN", "LESSON_PLAN", planId.toString(),
                trace(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + sanitizeFilename(topic) + "." + format + "\"")
                .header("X-Request-Id", trace(request))
                .contentType(contentType)
                .body(content);
    }

    @PostMapping("/plans/{planId}/share")
    public ResponseEntity<ApiResponse> sharePlan(@PathVariable("planId") UUID planId, HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);

        Map<String, Object> current = db.one("select share_token,is_shared from lesson_plans where id=?", planId);
        String shareToken = ApiDataMapper.asString(current.get("share_token"));
        if (shareToken == null || shareToken.isBlank()) {
            shareToken = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        }

        db.update(
                "update lesson_plans set is_shared=true,share_token=?,shared_at=coalesce(shared_at,now()),updated_at=now() where id=?",
                shareToken,
                planId);
        governance.audit(currentUser().userId(), currentUser().role(), "SHARE_PLAN", "LESSON_PLAN", planId.toString(),
                trace(request));

        Map<String, Object> data = Map.of(
                "planId", planId.toString(),
                "shareToken", shareToken,
                "shareUrl", "/api/v1/teacher/plans/shared/" + shareToken);
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/plans/shared/{shareToken}")
    public ResponseEntity<ApiResponse> getSharedPlan(@PathVariable("shareToken") String shareToken,
            HttpServletRequest request) {
        Map<String, Object> row = db.one(
                """
                        select id,topic,grade_level,duration_mins,content_md,is_shared,share_token,shared_at,created_at,updated_at
                        from lesson_plans
                        where share_token=? and is_shared=true and deleted_at is null
                        """,
                shareToken);
        return ResponseEntity.ok(ApiResponse.ok(toLessonPlanVo(row), trace(request)));
    }

    @GetMapping("/students/{studentId}/analytics")
    public ResponseEntity<ApiResponse> studentAnalytics(@PathVariable("studentId") UUID studentId,
            HttpServletRequest request) {
        requireRole("TEACHER");
        ensureStudentLinked(studentId);

        Map<String, Object> student = db
                .one("select username from users where id=? and role='STUDENT' and deleted_at is null", studentId);
        long totalExercises = db.count("select count(*) from exercise_records where student_id=?", studentId);
        long totalQuestions = db
                .count("select coalesce(sum(total_questions),0) from exercise_records where student_id=?", studentId);
        long correctCount = db.count("select coalesce(sum(correct_count),0) from exercise_records where student_id=?",
                studentId);
        double averageScore = ApiDataMapper.asDouble(
                db.one("select coalesce(avg(total_score),0) as avg_score from exercise_records where student_id=?",
                        studentId).get("avg_score"));
        long wrongBookCount = db.count("select count(*) from wrong_book where student_id=? and status='ACTIVE'",
                studentId);

        List<Map<String, Object>> weakRows = db.list(
                """
                        select kp.knowledge_point as knowledge_point, count(*) as wrong_count
                        from wrong_book w
                        join questions q on q.id=w.question_id
                        join lateral jsonb_array_elements_text(coalesce(q.knowledge_points, '[]'::jsonb)) as kp(knowledge_point) on true
                        where w.student_id=? and w.status='ACTIVE'
                        group by kp.knowledge_point
                        order by count(*) desc
                        limit 5
                        """,
                studentId);

        List<Map<String, Object>> topWeakPoints = weakRows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("knowledgePoint", String.valueOf(row.get("knowledge_point")));
            item.put("wrongCount", ApiDataMapper.asInt(row.get("wrong_count")));
            return item;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentId", studentId.toString());
        data.put("username", String.valueOf(student.get("username")));
        data.put("totalExercises", totalExercises);
        data.put("totalQuestions", totalQuestions);
        data.put("correctCount", correctCount);
        data.put("averageScore", averageScore);
        data.put("wrongBookCount", wrongBookCount);
        data.put("topWeakPoints", topWeakPoints);
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @PostMapping("/suggestions")
    public ResponseEntity<ApiResponse> createSuggestion(@Valid @RequestBody SuggestionReq req,
            HttpServletRequest request) {
        requireRole("TEACHER");
        AuthUser user = currentUser();

        UUID studentId = UUID.fromString(req.studentId());
        ensureStudentLinked(studentId);
        if ((req.questionId() == null || req.questionId().isBlank())
                && (req.knowledgePoint() == null || req.knowledgePoint().isBlank())) {
            throw new IllegalArgumentException("questionId 与 knowledgePoint 至少填写一个");
        }

        UUID questionId = null;
        if (req.questionId() != null && !req.questionId().isBlank()) {
            questionId = UUID.fromString(req.questionId());
        }

        UUID suggestionId = db.newId();
        db.update(
                "insert into teacher_suggestions(id,teacher_id,student_id,question_id,knowledge_point,suggestion) values (?,?,?,?,?,?)",
                suggestionId,
                user.userId(),
                studentId,
                questionId,
                req.knowledgePoint(),
                req.suggestion());
        Map<String, Object> row = db.one(
                "select id,teacher_id,student_id,question_id,knowledge_point,suggestion,created_at from teacher_suggestions where id=?",
                suggestionId);

        governance.audit(user.userId(), user.role(), "CREATE_SUGGESTION", "TEACHER_SUGGESTION", suggestionId.toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(toTeacherSuggestionVo(row), trace(request)));
    }

    private void processDocumentInBackground(
            UUID documentId,
            UUID teacherId,
            String filename,
            Path tempFile,
            String traceId,
            String idempotencyKey,
            UUID jobId) {
        try {
            governance.markJobRunning(jobId);
            db.update("update documents set status='PARSING',updated_at=now() where id=?", documentId);
            db.update("update documents set status='EMBEDDING',updated_at=now() where id=?", documentId);

            Map<String, Object> ingestResult = aiClient.ingestKb(Map.of(
                    "traceId", traceId,
                    "documentId", documentId.toString(),
                    "teacherId", teacherId.toString(),
                    "filename", filename,
                    "filePath", tempFile.toAbsolutePath().toString(),
                    "idempotencyKey", idempotencyKey == null ? "" : idempotencyKey));

            db.update("update documents set status='READY',error_message=null,updated_at=now() where id=?", documentId);
            governance.markJobSucceeded(jobId, Map.of(
                    "documentId", documentId.toString(),
                    "chunks", ingestResult.getOrDefault("chunks", 0)));
        } catch (Exception ex) {
            db.update("update documents set status='FAILED',error_message=?,updated_at=now() where id=?",
                    ex.getMessage(), documentId);
            governance.markJobDeadLetter(jobId, ex.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // ignore cleanup failure
            }
        }
    }

    private Map<String, Object> ensureDocumentOwner(UUID documentId) {
        Map<String, Object> row = db
                .one("select teacher_id,storage_path from documents where id=? and deleted_at is null", documentId);
        if (!currentUser().userId().equals(row.get("teacher_id"))) {
            throw new SecurityException("非资源归属者");
        }
        return row;
    }

    private void ensurePlanOwner(UUID planId) {
        Map<String, Object> row = db.one("select teacher_id from lesson_plans where id=? and deleted_at is null",
                planId);
        if (!currentUser().userId().equals(row.get("teacher_id"))) {
            throw new SecurityException("非资源归属者");
        }
    }

    private void ensureStudentLinked(UUID studentId) {
        boolean linked = db.exists(
                """
                        select 1
                        from teacher_student_bindings
                        where teacher_id=?
                          and student_id=?
                          and status='ACTIVE'
                          and (revoked_at is null or revoked_at > now())
                        """,
                currentUser().userId(),
                studentId);
        if (!linked) {
            throw new SecurityException("无权限访问该学生");
        }
    }

    private Map<String, Object> toDocumentVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("filename", String.valueOf(row.get("filename")));
        out.put("fileType", String.valueOf(row.get("file_type")));
        out.put("fileSize", ApiDataMapper.asLong(row.get("file_size")));
        out.put("status", String.valueOf(row.get("status")));
        out.put("errorMessage", ApiDataMapper.asString(row.get("error_message")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        out.put("updatedAt", ApiDataMapper.asIsoTime(row.get("updated_at")));
        return out;
    }

    private Map<String, Object> toLessonPlanVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("topic", String.valueOf(row.get("topic")));
        out.put("gradeLevel", String.valueOf(row.get("grade_level")));
        out.put("durationMins", ApiDataMapper.asInt(row.get("duration_mins")));
        out.put("contentMd", String.valueOf(row.get("content_md")));
        out.put("isShared", ApiDataMapper.asBoolean(row.get("is_shared")));
        out.put("shareToken", ApiDataMapper.asString(row.get("share_token")));
        out.put("sharedAt", ApiDataMapper.asIsoTime(row.get("shared_at")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        out.put("updatedAt", ApiDataMapper.asIsoTime(row.get("updated_at")));
        return out;
    }

    private Map<String, Object> toTeacherSuggestionVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("teacherId", String.valueOf(row.get("teacher_id")));
        out.put("studentId", String.valueOf(row.get("student_id")));
        out.put("questionId", ApiDataMapper.asString(row.get("question_id")));
        out.put("knowledgePoint", ApiDataMapper.asString(row.get("knowledge_point")));
        out.put("suggestion", String.valueOf(row.get("suggestion")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        return out;
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            StringBuilder builder = new StringBuilder();
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-\\u4e00-\\u9fa5]", "_");
    }

    private byte[] renderPdf(String title, String markdown) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            float leading = 16;
            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            Path cjkFont = Paths.get("C:/Windows/Fonts/simsun.ttc");
            if (Files.exists(cjkFont)) {
                try (var in = Files.newInputStream(cjkFont)) {
                    bodyFont = PDType0Font.load(document, in, true);
                    titleFont = bodyFont;
                }
            }

            PDPageContentStream stream = new PDPageContentStream(document, page);
            try {
                stream.setFont(titleFont, 14);
                stream.beginText();
                stream.newLineAtOffset(margin, y);
                stream.showText(safePdfText(title, bodyFont instanceof PDType0Font));
                stream.endText();

                stream.setFont(bodyFont, 11);
                y -= leading * 2;
                for (String line : wrapLines(markdown, 95)) {
                    if (y < margin) {
                        stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        stream.setFont(bodyFont, 11);
                        y = page.getMediaBox().getHeight() - margin;
                    }
                    stream.beginText();
                    stream.newLineAtOffset(margin, y);
                    stream.showText(safePdfText(line, bodyFont instanceof PDType0Font));
                    stream.endText();
                    y -= leading;
                }
            } finally {
                stream.close();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("PDF 导出失败", ex);
        }
    }

    private List<String> wrapLines(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.replace("\r", "").split("\n")) {
            if (raw.isEmpty()) {
                lines.add(" ");
                continue;
            }
            int start = 0;
            while (start < raw.length()) {
                int end = Math.min(raw.length(), start + width);
                lines.add(raw.substring(start, end));
                start = end;
            }
        }
        return lines;
    }

    private String safePdfText(String text, boolean cjkEnabled) {
        return cjkEnabled ? text : text.replaceAll("[^\\x20-\\x7E]", "?");
    }

    public record PlanGenerateReq(
            @NotBlank String topic,
            @NotBlank String gradeLevel,
            @Min(10) @Max(180) int durationMins) {
    }

    public record PlanUpdateReq(@NotBlank String contentMd) {
    }

    public record SuggestionReq(
            @NotBlank String studentId,
            String questionId,
            String knowledgePoint,
            @NotBlank @Size(min = 1, max = 2000) String suggestion) {
    }
}
