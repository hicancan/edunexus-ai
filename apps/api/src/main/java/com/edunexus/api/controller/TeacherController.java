package com.edunexus.api.controller;

import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.service.AiClient;
import com.edunexus.api.service.DbService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherController implements ControllerSupport {
    private final DbService db;
    private final AiClient aiClient;

    public TeacherController(DbService db, AiClient aiClient) {
        this.db = db;
        this.aiClient = aiClient;
    }

    @PostMapping("/knowledge/documents")
    public ResponseEntity<ApiResponse> uploadDocument(@RequestParam("file") MultipartFile file,
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
        UUID docId = db.newId();
        Path uploadDir = Paths.get("data", "uploads");
        Files.createDirectories(uploadDir);
        Path storage = uploadDir.resolve(docId + "-" + fileName);
        file.transferTo(storage);

        db.update("insert into documents(id,teacher_id,filename,file_type,file_size,storage_path,status) values (?,?,?,?,?,?,?)",
                docId, currentUser().userId(), fileName, fileType, fileSize, storage.toAbsolutePath().toString(), "UPLOADING");
        try {
            db.update("update documents set status='PARSING',updated_at=now() where id=?", docId);
            db.update("update documents set status='EMBEDDING',updated_at=now() where id=?", docId);
            Map<String, Object> ingestResp = aiClient.ingestKb(Map.of(
                    "document_id", docId.toString(),
                    "teacher_id", currentUser().userId().toString(),
                    "filename", fileName,
                    "file_path", storage.toAbsolutePath().toString()
            ));
            db.update("update documents set status='READY',updated_at=now() where id=?", docId);
            return ResponseEntity.status(202).body(ApiResponse.accepted(Map.of(
                    "id", docId,
                    "filename", fileName,
                    "status", "READY",
                    "chunks", ingestResp.getOrDefault("chunks", 0)
            ), trace(request)));
        } catch (Exception ex) {
            db.update("update documents set status='FAILED',error_message=?,updated_at=now() where id=?", ex.getMessage(), docId);
            throw ex;
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
        ensureDocOwner(documentId);
        aiClient.deleteKb(Map.of("document_id", documentId.toString()));
        db.update("update documents set deleted_at=now(),updated_at=now() where id=?", documentId);
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @PostMapping("/plans/generate")
    @Transactional
    public ResponseEntity<ApiResponse> generatePlan(@Valid @RequestBody PlanGenerateReq req, HttpServletRequest request) {
        requireRole("TEACHER");
        Map<String, Object> aiResp = aiClient.generatePlan(Map.of(
                "topic", req.topic(),
                "grade_level", req.gradeLevel(),
                "duration_mins", req.durationMins(),
                "scene", "lesson_plan"
        ));
        String content = String.valueOf(aiResp.getOrDefault("content", "# Lesson Plan\n生成失败"));
        UUID planId = db.newId();
        db.update("insert into lesson_plans(id,teacher_id,topic,grade_level,duration_mins,content_md) values (?,?,?,?,?,?)",
                planId, currentUser().userId(), req.topic(), req.gradeLevel(), req.durationMins(), content);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", planId, "topic", req.topic(), "content", content, "createdAt", Instant.now().toString()), trace(request)));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse> listPlans(@RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 HttpServletRequest request) {
        requireRole("TEACHER");
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = db.list("select id,topic,grade_level as \"gradeLevel\",duration_mins as \"durationMins\",updated_at as \"updatedAt\",is_shared as \"isShared\" from lesson_plans where teacher_id=? and deleted_at is null order by updated_at desc limit ? offset ?",
                currentUser().userId(), size, offset);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", list, "page", page, "size", size), trace(request)));
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse> updatePlan(@PathVariable("planId") UUID planId, @Valid @RequestBody PlanUpdateReq req, HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);
        db.update("update lesson_plans set content_md=?,updated_at=now() where id=?", req.content(), planId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", planId), trace(request)));
    }

    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse> deletePlan(@PathVariable("planId") UUID planId, HttpServletRequest request) {
        requireRole("TEACHER");
        ensurePlanOwner(planId);
        db.update("update lesson_plans set deleted_at=now(),updated_at=now() where id=?", planId);
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
        byte[] bytes = String.valueOf(row.get("content_md")).getBytes(StandardCharsets.UTF_8);
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
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id), trace(request)));
    }

    private void ensureDocOwner(UUID docId) {
        Map<String, Object> row = db.one("select teacher_id from documents where id=?", docId);
        if (!currentUser().userId().equals(row.get("teacher_id"))) throw new SecurityException("无权限");
    }

    private void ensurePlanOwner(UUID planId) {
        Map<String, Object> row = db.one("select teacher_id from lesson_plans where id=?", planId);
        if (!currentUser().userId().equals(row.get("teacher_id"))) throw new SecurityException("无权限");
    }

    private void ensureStudentLinked(UUID studentId) {
        boolean linked = db.exists(
                "select 1 from student_teacher_relations where teacher_id=? and student_id=?",
                currentUser().userId(),
                studentId
        );
        if (!linked) throw new SecurityException("无权限访问该学生");
    }

    private String safeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-\\u4e00-\\u9fa5]", "_");
    }

    public record PlanGenerateReq(@NotBlank String topic, @NotBlank String gradeLevel, int durationMins) {}
    public record PlanUpdateReq(@NotBlank String content) {}
    public record SuggestionReq(@NotBlank String studentId, String questionId, String knowledgePoint, @NotBlank String suggestion) {}
}
