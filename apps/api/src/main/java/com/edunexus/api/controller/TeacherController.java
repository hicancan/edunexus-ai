package com.edunexus.api.controller;

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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController implements ControllerSupport {
    private final DbService db;
    private final AiClient aiClient;
    private final ObjectStorageService storage;
    private final GovernanceService governance;

    public TeacherController(DbService db, AiClient aiClient, ObjectStorageService storage, GovernanceService governance) {
        this.db = db;
        this.aiClient = aiClient;
        this.storage = storage;
        this.governance = governance;
    }

    @PostMapping("/knowledge/documents")
    public ResponseEntity<ApiResponse> uploadDocument(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                      @RequestParam("file") MultipartFile file,
                                                      HttpServletRequest request) throws IOException {
        requireRole("TEACHER");
        if (file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("upload.bin");
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".pdf") || lowerName.endsWith(".docx") || lowerName.endsWith(".doc"))) {
            throw new IllegalArgumentException("仅支持 PDF/Docx 文件");
        }
        String fileType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        long fileSize = file.getSize();
        byte[] fileBytes = file.getBytes();
        String requestHash = governance.requestHash(Map.of(
                "teacherId", currentUser().userId(),
                "filename", fileName,
                "fileType", fileType,
                "fileSize", fileSize,
                "contentSha256", sha256Hex(fileBytes)
        ));
        Map<String, Object> replay = governance.getIdempotentReplay("teacher.kb.upload", idempotencyKey, requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }
        UUID docId = db.newId();
        UUID jobId = governance.createJobRun("DOCUMENT_INGEST", docId, Map.of(
                "teacherId", currentUser().userId().toString(),
                "filename", fileName,
                "fileType", fileType,
                "fileSize", fileSize
        ));
        String storagePath = storage.upload(fileName, fileType, fileBytes);
        Path tempPath = Files.createTempFile("edunexus-kb-" + docId, "-" + safeFilename(fileName));
        Files.write(tempPath, fileBytes);

        db.update("insert into documents(id,teacher_id,filename,file_type,file_size,storage_path,status) values (?,?,?,?,?,?,?)",
                docId, currentUser().userId(), fileName, fileType, fileSize, storagePath, "UPLOADING");
        try {
            governance.markJobRunning(jobId);
            db.update("update documents set status='PARSING',updated_at=now() where id=?", docId);
            db.update("update documents set status='EMBEDDING',updated_at=now() where id=?", docId);
            Map<String, Object> ingestResp = aiClient.ingestKb(Map.of(
                    "document_id", docId.toString(),
                    "teacher_id", currentUser().userId().toString(),
                    "filename", fileName,
                    "file_path", tempPath.toAbsolutePath().toString(),
                    "trace_id", trace(request),
                    "idempotency_key", idempotencyKey == null ? "" : idempotencyKey
            ));
            db.update("update documents set status='READY',updated_at=now() where id=?", docId);
            Map<String, Object> data = Map.of(
                    "id", docId,
                    "jobId", jobId,
                    "filename", fileName,
                    "status", "READY",
                    "chunks", ingestResp.getOrDefault("chunks", 0)
            );
            governance.markJobSucceeded(jobId, data);
            governance.storeIdempotency("teacher.kb.upload", idempotencyKey, requestHash, data, Duration.ofHours(24));
            governance.audit(currentUser().userId(), currentUser().role(), "UPLOAD_DOCUMENT", "DOCUMENT", docId.toString(), trace(request));
            return ResponseEntity.status(202).body(ApiResponse.accepted(data, trace(request)));
        } catch (Exception ex) {
            db.update("update documents set status='FAILED',error_message=?,updated_at=now() where id=?", ex.getMessage(), docId);
            governance.markJobFailed(jobId, ex.getMessage());
            governance.audit(currentUser().userId(), currentUser().role(), "UPLOAD_DOCUMENT_FAILED", "DOCUMENT", docId.toString(), trace(request));
            throw ex;
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    @GetMapping("/knowledge/documents")
    public ResponseEntity<ApiResponse> listDocuments(@RequestParam(value = "status", required = false) String status, HttpServletRequest request) {
        requireRole("TEACHER");
        String sql = "select id,filename,status,created_at as \"createdAt\",updated_at as \"updatedAt\",error_message as \"errorMessage\" from documents where teacher_id=? and deleted_at is null";
        List<Object> args = new ArrayList<>();
        args.add(currentUser().userId());
        if (status != null && !status.isBlank()) {
            sql += " and status=?";
            args.add(status);
        }
        sql += " order by created_at desc";
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", db.list(sql, args.toArray())), trace(request)));
    }

    @DeleteMapping("/knowledge/documents/{documentId}")
    public ResponseEntity<ApiResponse> deleteDocument(@PathVariable("documentId") UUID documentId, HttpServletRequest request) {
        requireRole("TEACHER");
        Map<String, Object> doc = ensureDocOwner(documentId);
        aiClient.deleteKb(Map.of("document_id", documentId.toString(), "trace_id", trace(request)));
        String storagePath = String.valueOf(doc.get("storage_path"));
        if (storagePath.startsWith("s3://")) {
            storage.delete(storagePath);
        } else {
            try {
                Files.deleteIfExists(Paths.get(storagePath));
            } catch (IOException ignored) {
                // ignore local cleanup failure
            }
        }
        db.update("update documents set deleted_at=now(),updated_at=now() where id=?", documentId);
        governance.audit(currentUser().userId(), currentUser().role(), "DELETE_DOCUMENT", "DOCUMENT", documentId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @PostMapping("/plans/generate")
    @Transactional
    public ResponseEntity<ApiResponse> generatePlan(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                    @Valid @RequestBody PlanGenerateReq req,
                                                    HttpServletRequest request) {
        requireRole("TEACHER");
        String requestHash = governance.requestHash(Map.of("teacherId", currentUser().userId(), "payload", req));
        Map<String, Object> replay = governance.getIdempotentReplay("teacher.plan.generate", idempotencyKey, requestHash);
        if (replay != null) {
            return ResponseEntity.ok(ApiResponse.ok(replay, trace(request)));
        }
        Map<String, Object> aiResp = aiClient.generatePlan(Map.of(
                "topic", req.topic(),
                "grade_level", req.gradeLevel(),
                "duration_mins", req.durationMins(),
                "trace_id", trace(request),
                "scene", "lesson_plan",
                "teacher_id", currentUser().userId().toString(),
                "idempotency_key", idempotencyKey == null ? "" : idempotencyKey
        ));
        String content = String.valueOf(aiResp.getOrDefault("contentMd", aiResp.getOrDefault("content", "# Lesson Plan\n生成失败")));
        UUID planId = db.newId();
        db.update("insert into lesson_plans(id,teacher_id,topic,grade_level,duration_mins,content_md) values (?,?,?,?,?,?)",
                planId, currentUser().userId(), req.topic(), req.gradeLevel(), req.durationMins(), content);
        Map<String, Object> data = Map.of("id", planId, "topic", req.topic(), "content", content, "createdAt", Instant.now().toString());
        governance.storeIdempotency("teacher.plan.generate", idempotencyKey, requestHash, data, Duration.ofHours(24));
        governance.audit(currentUser().userId(), currentUser().role(), "GENERATE_PLAN", "LESSON_PLAN", planId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse> listPlans(@RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 HttpServletRequest request) {
        requireRole("TEACHER");
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = db.list("select id,topic,grade_level as \"gradeLevel\",duration_mins as \"durationMins\",content_md as content,updated_at as \"updatedAt\",is_shared as \"isShared\" from lesson_plans where teacher_id=? and deleted_at is null order by updated_at desc limit ? offset ?",
                currentUser().userId(), size, offset);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", list, "page", page, "size", size), trace(request)));
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse> updatePlan(@PathVariable("planId") UUID planId, @Valid @RequestBody PlanUpdateReq req, HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);
        String content = req.contentMd() == null || req.contentMd().isBlank() ? req.content() : req.contentMd();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("contentMd 不能为空");
        }
        db.update("update lesson_plans set content_md=?,updated_at=now() where id=?", content, planId);
        governance.audit(currentUser().userId(), currentUser().role(), "UPDATE_PLAN", "LESSON_PLAN", planId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", planId), trace(request)));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse> deletePlan(@PathVariable("planId") UUID planId, HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);
        db.update("update lesson_plans set deleted_at=now(),updated_at=now() where id=?", planId);
        governance.audit(currentUser().userId(), currentUser().role(), "DELETE_PLAN", "LESSON_PLAN", planId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @GetMapping("/plans/{planId}/export")
    public ResponseEntity<byte[]> exportPlan(@PathVariable("planId") UUID planId,
                                             @RequestParam(value = "format", defaultValue = "md") String format,
                                             HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);
        if (!"md".equals(format) && !"pdf".equals(format)) {
            throw new IllegalArgumentException("format 仅支持 md/pdf");
        }
        Map<String, Object> row = db.one("select id,topic,content_md from lesson_plans where id=?", planId);
        String filename = safeFilename(String.valueOf(row.get("topic"))) + "." + format;
        String content = String.valueOf(row.get("content_md"));
        byte[] bytes = "pdf".equals(format)
                ? renderPdf(String.valueOf(row.get("topic")), content)
                : content.getBytes(StandardCharsets.UTF_8);
        governance.audit(currentUser().userId(), currentUser().role(), "EXPORT_PLAN", "LESSON_PLAN", planId.toString(), trace(request));
        MediaType mediaType = "pdf".equals(format)
                ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("text/markdown; charset=UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Request-Id", trace(request))
                .contentType(mediaType)
                .body(bytes);
    }

    @PostMapping("/plans/{planId}/share")
    public ResponseEntity<ApiResponse> sharePlan(@PathVariable("planId") UUID planId, HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        db.update("update lesson_plans set is_shared=true,share_token=?,shared_at=now(),updated_at=now() where id=?", token, planId);
        governance.audit(currentUser().userId(), currentUser().role(), "SHARE_PLAN", "LESSON_PLAN", planId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "planId", planId,
                "shareToken", token,
                "shareUrl", "/api/v1/teacher/plans/shared/" + token
        ), trace(request)));
    }

    @GetMapping("/plans/shared/{shareToken}")
    public ResponseEntity<ApiResponse> getSharedPlan(@PathVariable("shareToken") String shareToken, HttpServletRequest request) {
        Map<String, Object> row = db.one("select id,topic,grade_level as \"gradeLevel\",duration_mins as \"durationMins\",content_md as content from lesson_plans where share_token=? and is_shared=true and deleted_at is null", shareToken);
        return ResponseEntity.ok(ApiResponse.ok(row, trace(request)));
    }

    @GetMapping("/students/{studentId}/analytics")
    public ResponseEntity<ApiResponse> studentAnalytics(@PathVariable("studentId") UUID studentId, HttpServletRequest request) {
        requireRole("TEACHER");
        ensureStudentLinked(studentId);
        Number total = (Number) db.one("select count(*) as c from exercise_records where student_id=?", studentId).get("c");
        Number avg = (Number) db.one("select coalesce(avg(total_score),0) as a from exercise_records where student_id=?", studentId).get("a");
        List<Map<String, Object>> wrong = db.list("""
                select q.subject as concept, count(*) as errorCount
                from wrong_book w join questions q on w.question_id=q.id
                where w.student_id=? and w.status='ACTIVE'
                group by q.subject order by count(*) desc limit 5
                """, studentId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "studentId", studentId,
                "totalExercises", total,
                "averageScore", avg,
                "frequentWrongConcepts", wrong
        ), trace(request)));
    }

    @PostMapping("/suggestions")
    public ResponseEntity<ApiResponse> createSuggestion(@Valid @RequestBody SuggestionReq req, HttpServletRequest request) {
        requireRole("TEACHER");
        if ((req.questionId() == null || req.questionId().isBlank()) && (req.knowledgePoint() == null || req.knowledgePoint().isBlank())) {
            throw new IllegalArgumentException("questionId 与 knowledgePoint 至少填写一个");
        }
        UUID studentId = UUID.fromString(req.studentId());
        ensureStudentLinked(studentId);
        UUID id = db.newId();
        db.update("insert into teacher_suggestions(id,teacher_id,student_id,question_id,knowledge_point,suggestion) values (?,?,?,?,?,?)",
                id,
                currentUser().userId(),
                studentId,
                req.questionId() == null || req.questionId().isBlank() ? null : UUID.fromString(req.questionId()),
                req.knowledgePoint(),
                req.suggestion());
        governance.audit(currentUser().userId(), currentUser().role(), "CREATE_SUGGESTION", "TEACHER_SUGGESTION", id.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id), trace(request)));
    }

    private Map<String, Object> ensureDocOwner(UUID docId) {
        Map<String, Object> row = db.one("select teacher_id,storage_path from documents where id=?", docId);
        if (!currentUser().userId().equals(row.get("teacher_id"))) throw new SecurityException("无权限");
        return row;
    }

    private void ensurePlanOwner(UUID planId) {
        Map<String, Object> row = db.one("select teacher_id from lesson_plans where id=?", planId);
        if (!currentUser().userId().equals(row.get("teacher_id"))) throw new SecurityException("无权限");
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
                studentId
        );
        if (!linked) throw new SecurityException("无权限访问该学生");
    }

    private String safeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-\\u4e00-\\u9fa5]", "_");
    }

    private String sha256Hex(byte[] payload) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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

    public record PlanGenerateReq(@NotBlank String topic, @NotBlank String gradeLevel, @Min(10) @Max(180) int durationMins) {}
    public record PlanUpdateReq(String contentMd, String content) {}
    public record SuggestionReq(@NotBlank String studentId, String questionId, String knowledgePoint, @NotBlank String suggestion) {}
}
