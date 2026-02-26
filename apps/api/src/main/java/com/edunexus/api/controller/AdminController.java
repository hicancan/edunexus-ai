package com.edunexus.api.controller;

import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.service.DbService;
import com.edunexus.api.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController implements ControllerSupport {
    private final DbService db;
    private final PasswordEncoder encoder;
    private final ObjectStorageService storage;

    public AdminController(DbService db, PasswordEncoder encoder, ObjectStorageService storage) {
        this.db = db;
        this.encoder = encoder;
        this.storage = storage;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse> listUsers(@RequestParam(value = "role", required = false) String role,
                                                 @RequestParam(value = "status", required = false) String status,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 HttpServletRequest request) {
        requireRole("ADMIN");
        String sql = "select id,username,email,phone,role,status,created_at as \"createdAt\" from users where deleted_at is null";
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            sql += " and role=?";
            args.add(role);
        }
        if (status != null && !status.isBlank()) {
            sql += " and status=?";
            args.add(status);
        }
        sql += " order by created_at desc limit ? offset ?";
        args.add(size);
        args.add((page - 1) * size);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", db.list(sql, args.toArray()), "page", page, "size", size), trace(request)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse> createUser(@Valid @RequestBody AdminUserCreateReq req, HttpServletRequest request) {
        requireRole("ADMIN");
        if (db.exists("select 1 from users where username=? and deleted_at is null", req.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        UUID id = db.newId();
        db.update("insert into users(id,username,password_hash,email,phone,role,status) values (?,?,?,?,?,?,?)",
                id, req.username(), encoder.encode(req.password()), req.email(), req.phone(), req.role(), "ACTIVE");
        audit("CREATE_USER", "USER", id.toString());
        return ResponseEntity.status(201).body(ApiResponse.created(Map.of("id", id), trace(request)));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<ApiResponse> patchUser(@PathVariable("userId") UUID userId, @Valid @RequestBody AdminUserPatchReq req, HttpServletRequest request) {
        requireRole("ADMIN");
        Map<String, Object> user = db.one("select role,status from users where id=? and deleted_at is null", userId);
        String role = req.role() == null || req.role().isBlank() ? String.valueOf(user.get("role")) : req.role();
        String status = req.status() == null || req.status().isBlank() ? String.valueOf(user.get("status")) : req.status();
        db.update("update users set role=?,status=?,updated_at=now() where id=?", role, status, userId);
        audit("PATCH_USER", "USER", userId.toString());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", userId, "role", role, "status", status), trace(request)));
    }

    @GetMapping("/resources")
    public ResponseEntity<ApiResponse> resources(@RequestParam(value = "resourceType", required = false) String resourceType,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size,
                                                 HttpServletRequest request) {
        requireRole("ADMIN");
        if ("LESSON_PLAN".equals(resourceType)) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("list", db.list("select id,topic as name,'LESSON_PLAN' as type,updated_at as \"updatedAt\" from lesson_plans where deleted_at is null order by updated_at desc limit ? offset ?", size, (page - 1) * size)), trace(request)));
        }
        if ("DOCUMENT".equals(resourceType)) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("list", db.list("select id,filename as name,'DOCUMENT' as type,updated_at as \"updatedAt\" from documents where deleted_at is null order by updated_at desc limit ? offset ?", size, (page - 1) * size)), trace(request)));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", db.list("select id,content as name,'QUESTION' as type,updated_at as \"updatedAt\" from questions where is_active=true order by updated_at desc limit ? offset ?", size, (page - 1) * size)), trace(request)));
    }

    @GetMapping("/resources/{resourceId}/download")
    public ResponseEntity<byte[]> download(@PathVariable("resourceId") UUID resourceId, HttpServletRequest request) {
        requireRole("ADMIN");
        String filename;
        byte[] bytes;
        MediaType contentType;
        if (db.exists("select 1 from lesson_plans where id=?", resourceId)) {
            Map<String, Object> data = db.one("select topic,content_md as content from lesson_plans where id=?", resourceId);
            filename = safeFilename(String.valueOf(data.get("topic"))) + ".md";
            bytes = String.valueOf(data.get("content")).getBytes(StandardCharsets.UTF_8);
            contentType = MediaType.parseMediaType("text/markdown; charset=UTF-8");
        } else if (db.exists("select 1 from documents where id=?", resourceId)) {
            Map<String, Object> data = db.one("select filename,storage_path from documents where id=?", resourceId);
            String path = String.valueOf(data.get("storage_path"));
            if (path.startsWith("s3://")) {
                bytes = storage.download(path);
            } else {
                java.nio.file.Path file = java.nio.file.Paths.get(path);
                if (!java.nio.file.Files.exists(file)) {
                    throw new IllegalArgumentException("资源文件不存在");
                }
                try {
                    bytes = java.nio.file.Files.readAllBytes(file);
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }
            filename = safeFilename(String.valueOf(data.get("filename")));
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        } else {
            Map<String, Object> data = db.one("select content,analysis from questions where id=?", resourceId);
            String text = "题干:\n" + String.valueOf(data.get("content")) + "\n\n解析:\n" + String.valueOf(data.get("analysis"));
            filename = "question-" + resourceId + ".txt";
            bytes = text.getBytes(StandardCharsets.UTF_8);
            contentType = MediaType.TEXT_PLAIN;
        }
        audit("DOWNLOAD_RESOURCE", "RESOURCE", resourceId.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Request-Id", trace(request))
                .contentType(contentType)
                .body(bytes);
    }

    @GetMapping("/audits")
    public ResponseEntity<ApiResponse> audits(@RequestParam(value = "page", defaultValue = "1") int page,
                                              @RequestParam(value = "size", defaultValue = "20") int size,
                                              HttpServletRequest request) {
        requireRole("ADMIN");
        List<Map<String, Object>> list = db.list("select id,actor_id as \"actorId\",actor_role as \"actorRole\",action,resource_type as \"resourceType\",resource_id as \"resourceId\",created_at as \"createdAt\" from audit_logs order by created_at desc limit ? offset ?",
                size, (page - 1) * size);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("list", list, "page", page, "size", size), trace(request)));
    }

    @GetMapping("/dashboard/metrics")
    public ResponseEntity<ApiResponse> dashboard(HttpServletRequest request) {
        requireRole("ADMIN");
        Number totalUsers = (Number) db.one("select count(*) as c from users where deleted_at is null").get("c");
        Number totalStudents = (Number) db.one("select count(*) as c from users where role='STUDENT' and deleted_at is null").get("c");
        Number totalTeachers = (Number) db.one("select count(*) as c from users where role='TEACHER' and deleted_at is null").get("c");
        Number dailyChats = (Number) db.one("select count(*) as c from chat_messages where role='USER' and created_at::date=now()::date").get("c");
        Number docs = (Number) db.one("select count(*) as c from documents where deleted_at is null").get("c");
        Number vectors = (Number) db.one("select count(*) as c from documents where status='READY' and deleted_at is null").get("c");
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "totalUsers", totalUsers,
                "totalStudents", totalStudents,
                "totalTeachers", totalTeachers,
                "dailyAiChats", dailyChats,
                "totalKbDocuments", docs,
                "totalVectorIndexes", vectors
        ), trace(request)));
    }

    private void audit(String action, String resourceType, String resourceId) {
        db.update("insert into audit_logs(id,actor_id,actor_role,action,resource_type,resource_id) values (?,?,?,?,?,?)",
                db.newId(), currentUser().userId(), currentUser().role(), action, resourceType, resourceId);
    }

    private String safeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-\\u4e00-\\u9fa5]", "_");
    }

    public record AdminUserCreateReq(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank @Pattern(regexp = "STUDENT|TEACHER|ADMIN") String role,
            String email,
            String phone
    ) {}
    public record AdminUserPatchReq(
            @Pattern(regexp = "STUDENT|TEACHER|ADMIN") String role,
            @Pattern(regexp = "ACTIVE|DISABLED") String status
    ) {}
}
