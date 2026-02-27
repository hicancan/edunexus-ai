package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.common.ConflictException;
import com.edunexus.api.common.FilenameUtil;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.service.DbService;
import com.edunexus.api.service.GovernanceService;
import com.edunexus.api.service.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController implements ControllerSupport {
    private final DbService db;
    private final PasswordEncoder passwordEncoder;
    private final ObjectStorageService objectStorageService;
    private final GovernanceService governance;
    private final ObjectMapper objectMapper;

    public AdminController(
            DbService db,
            PasswordEncoder passwordEncoder,
            ObjectStorageService objectStorageService,
            GovernanceService governance,
            ObjectMapper objectMapper
    ) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.objectStorageService = objectStorageService;
        this.governance = governance;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse> listUsers(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        requireRole("ADMIN");
        if (role != null && !role.isBlank()
                && !"STUDENT".equals(role)
                && !"TEACHER".equals(role)
                && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("role 仅支持 STUDENT/TEACHER/ADMIN");
        }
        if (status != null && !status.isBlank()
                && !"ACTIVE".equals(status)
                && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("status 仅支持 ACTIVE/DISABLED");
        }

        StringBuilder where = new StringBuilder(" where deleted_at is null");
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            where.append(" and role=?");
            args.add(role);
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status=?");
            args.add(status);
        }

        int offset = (page - 1) * size;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add(offset);

        List<Map<String, Object>> rows = db.list(
                """
                select id,username,role,status,email,phone,created_at,updated_at
                from users
                """ + where + " order by created_at desc limit ? offset ?",
                listArgs.toArray()
        );
        long total = db.count("select count(*) from users" + where, args.toArray());

        List<Map<String, Object>> content = rows.stream().map(this::toUserVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse> createUser(@Valid @RequestBody AdminUserCreateReq req, HttpServletRequest request) {
        requireRole("ADMIN");
        if (db.exists("select 1 from users where username=? and deleted_at is null", req.username())) {
            throw new ConflictException("用户名已存在");
        }

        UUID userId = db.newId();
        db.update(
                "insert into users(id,username,password_hash,email,phone,role,status) values (?,?,?,?,?,?, 'ACTIVE')",
                userId,
                req.username(),
                passwordEncoder.encode(req.password()),
                req.email(),
                req.phone(),
                req.role()
        );

        Map<String, Object> user = db.one("select id,username,role,status,email,phone,created_at,updated_at from users where id=?", userId);
        audit("CREATE_USER", "USER", userId.toString(), request);
        return ResponseEntity.status(201).body(ApiResponse.created(toUserVo(user), trace(request)));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<ApiResponse> patchUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AdminUserPatchReq req,
            HttpServletRequest request
    ) {
        requireRole("ADMIN");
        Map<String, Object> existing = db.one("select role,status from users where id=? and deleted_at is null", userId);

        String role = req.role() == null || req.role().isBlank() ? String.valueOf(existing.get("role")) : req.role();
        String status = req.status() == null || req.status().isBlank() ? String.valueOf(existing.get("status")) : req.status();
        db.update("update users set role=?,status=?,updated_at=now() where id=?", role, status, userId);

        Map<String, Object> user = db.one("select id,username,role,status,email,phone,created_at,updated_at from users where id=?", userId);
        audit("PATCH_USER", "USER", userId.toString(), request);
        return ResponseEntity.ok(ApiResponse.ok(toUserVo(user), trace(request)));
    }

    @GetMapping("/resources")
    public ResponseEntity<ApiResponse> listResources(
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        requireRole("ADMIN");
        if (resourceType != null && !resourceType.isBlank()
                && !"LESSON_PLAN".equals(resourceType)
                && !"QUESTION".equals(resourceType)
                && !"DOCUMENT".equals(resourceType)) {
            throw new IllegalArgumentException("resourceType 仅支持 LESSON_PLAN/QUESTION/DOCUMENT");
        }

        String resourceCte = """
                with resources as (
                  select
                    lp.id as resource_id,
                    'LESSON_PLAN'::varchar as resource_type,
                    lp.topic as title,
                    lp.teacher_id as creator_id,
                    u.username as creator_username,
                    lp.created_at as created_at
                  from lesson_plans lp
                  join users u on u.id=lp.teacher_id
                  where lp.deleted_at is null

                  union all

                  select
                    q.id as resource_id,
                    'QUESTION'::varchar as resource_type,
                    left(q.content, 120) as title,
                    q.created_by as creator_id,
                    u.username as creator_username,
                    q.created_at as created_at
                  from questions q
                  left join users u on u.id=q.created_by
                  where q.is_active=true

                  union all

                  select
                    d.id as resource_id,
                    'DOCUMENT'::varchar as resource_type,
                    d.filename as title,
                    d.teacher_id as creator_id,
                    u.username as creator_username,
                    d.created_at as created_at
                  from documents d
                  join users u on u.id=d.teacher_id
                  where d.deleted_at is null
                )
                """;

        List<Object> args = new ArrayList<>();
        String filter = "";
        if (resourceType != null && !resourceType.isBlank()) {
            filter = " where resource_type=?";
            args.add(resourceType);
        }

        int offset = (page - 1) * size;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add(offset);

        List<Map<String, Object>> rows = db.list(
                resourceCte + " select resource_id,resource_type,title,creator_id,creator_username,created_at from resources"
                        + filter + " order by created_at desc limit ? offset ?",
                listArgs.toArray()
        );
        long total = db.count(resourceCte + " select count(*) from resources" + filter, args.toArray());

        List<Map<String, Object>> content = rows.stream().map(this::toAdminResourceVo).toList();
        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @GetMapping("/resources/{resourceId}/download")
    public ResponseEntity<byte[]> downloadResource(@PathVariable("resourceId") UUID resourceId, HttpServletRequest request) {
        requireRole("ADMIN");

        DownloadData downloadData = resolveDownloadData(resourceId);
        audit("DOWNLOAD_RESOURCE", "RESOURCE", resourceId.toString(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadData.filename() + "\"")
                .header("X-Request-Id", trace(request))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(downloadData.bytes());
    }

    @GetMapping("/audits")
    public ResponseEntity<ApiResponse> listAudits(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        requireRole("ADMIN");
        int offset = (page - 1) * size;

        List<Map<String, Object>> rows = db.list(
                """
                select id,actor_id,actor_role,action,resource_type,resource_id,detail,ip,created_at
                from audit_logs
                order by created_at desc
                limit ? offset ?
                """,
                size,
                offset
        );
        long total = db.count("select count(*) from audit_logs");
        List<Map<String, Object>> content = rows.stream().map(this::toAuditVo).toList();

        return ResponseEntity.ok(ApiResponse.ok(ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @GetMapping("/dashboard/metrics")
    public ResponseEntity<ApiResponse> dashboardMetrics(HttpServletRequest request) {
        requireRole("ADMIN");

        long totalUsers = db.count("select count(*) from users where deleted_at is null");
        long totalStudents = db.count("select count(*) from users where role='STUDENT' and deleted_at is null");
        long totalTeachers = db.count("select count(*) from users where role='TEACHER' and deleted_at is null");
        long totalChatSessions = db.count("select count(*) from chat_sessions where is_deleted=false");
        long totalExerciseRecords = db.count("select count(*) from exercise_records");
        long totalDocuments = db.count("select count(*) from documents where deleted_at is null");
        long totalKnowledgeChunks = db.count(
                """
                select coalesce(sum((result->>'chunks')::bigint), 0)
                from job_runs
                where job_type='DOCUMENT_INGEST' and status='SUCCEEDED' and jsonb_exists(result, 'chunks')
                """
        );
        long totalLessonPlans = db.count("select count(*) from lesson_plans where deleted_at is null");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", totalUsers);
        data.put("totalStudents", totalStudents);
        data.put("totalTeachers", totalTeachers);
        data.put("totalChatSessions", totalChatSessions);
        data.put("totalExerciseRecords", totalExerciseRecords);
        data.put("totalDocuments", totalDocuments);
        data.put("totalKnowledgeChunks", totalKnowledgeChunks);
        data.put("totalLessonPlans", totalLessonPlans);
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    private void audit(String action, String resourceType, String resourceId, HttpServletRequest request) {
        AuthUser user = currentUser();
        governance.audit(user.userId(), user.role(), action, resourceType, resourceId, trace(request));
    }

    private Map<String, Object> toUserVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("username", String.valueOf(row.get("username")));
        out.put("role", String.valueOf(row.get("role")));
        out.put("status", String.valueOf(row.get("status")));
        out.put("email", ApiDataMapper.asString(row.get("email")));
        out.put("phone", ApiDataMapper.asString(row.get("phone")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        out.put("updatedAt", ApiDataMapper.asIsoTime(row.get("updated_at")));
        return out;
    }

    private Map<String, Object> toAdminResourceVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resourceId", String.valueOf(row.get("resource_id")));
        out.put("resourceType", String.valueOf(row.get("resource_type")));
        out.put("title", String.valueOf(row.get("title")));
        out.put("creatorId", ApiDataMapper.asString(row.get("creator_id")));
        out.put("creatorUsername", ApiDataMapper.asString(row.get("creator_username")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        return out;
    }

    private Map<String, Object> toAuditVo(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get("id")));
        out.put("actorId", ApiDataMapper.asString(row.get("actor_id")));
        out.put("actorRole", String.valueOf(row.get("actor_role")));
        out.put("action", String.valueOf(row.get("action")));
        out.put("resourceType", String.valueOf(row.get("resource_type")));
        out.put("resourceId", String.valueOf(row.get("resource_id")));
        out.put("detail", ApiDataMapper.parseJsonValue(row.get("detail"), objectMapper));
        out.put("ip", ApiDataMapper.asString(row.get("ip")));
        out.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        return out;
    }

    private DownloadData resolveDownloadData(UUID resourceId) {
        Map<String, Object> lessonPlan = db.oneOrNull("select topic,content_md from lesson_plans where id=? and deleted_at is null", resourceId);
        if (lessonPlan != null) {
            String filename = FilenameUtil.sanitize(String.valueOf(lessonPlan.get("topic"))) + ".md";
            byte[] bytes = String.valueOf(lessonPlan.get("content_md")).getBytes(StandardCharsets.UTF_8);
            return new DownloadData(filename, bytes);
        }

        Map<String, Object> document = db.oneOrNull("select filename,storage_path from documents where id=? and deleted_at is null", resourceId);
        if (document != null) {
            String filename = FilenameUtil.sanitize(String.valueOf(document.get("filename")));
            byte[] bytes = objectStorageService.download(String.valueOf(document.get("storage_path")));
            return new DownloadData(filename, bytes);
        }

        Map<String, Object> question = db.oneOrNull("select content,analysis from questions where id=? and is_active=true", resourceId);
        if (question != null) {
            String payload = "题干:\n" + String.valueOf(question.get("content")) + "\n\n解析:\n" + String.valueOf(question.get("analysis"));
            return new DownloadData("question-" + resourceId + ".txt", payload.getBytes(StandardCharsets.UTF_8));
        }

        throw new ResourceNotFoundException("资源不存在");
    }

    private record DownloadData(String filename, byte[] bytes) {
    }

    public record AdminUserCreateReq(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank @Pattern(regexp = "STUDENT|TEACHER|ADMIN") String role,
            @Email String email,
            String phone
    ) {
    }

    public record AdminUserPatchReq(
            @Pattern(regexp = "STUDENT|TEACHER|ADMIN") String role,
            @Pattern(regexp = "ACTIVE|DISABLED") String status
    ) {
    }
}
