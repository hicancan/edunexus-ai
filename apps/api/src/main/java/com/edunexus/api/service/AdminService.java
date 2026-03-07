package com.edunexus.api.service;

import com.edunexus.api.common.ConflictException;
import com.edunexus.api.common.FilenameUtil;
import com.edunexus.api.common.ResourceNotFoundException;
import com.edunexus.api.domain.AdminResource;
import com.edunexus.api.domain.AuditLog;
import com.edunexus.api.domain.DashboardMetrics;
import com.edunexus.api.domain.User;
import com.edunexus.api.repository.AdminResourceRepository;
import com.edunexus.api.repository.AuditRepository;
import com.edunexus.api.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final UserRepository userRepo;
    private final AdminResourceRepository resourceRepo;
    private final AuditRepository auditRepo;
    private final ObjectStorageService objectStorageService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public AdminService(
            UserRepository userRepo,
            AdminResourceRepository resourceRepo,
            AuditRepository auditRepo,
            ObjectStorageService objectStorageService,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc) {
        this.userRepo = userRepo;
        this.resourceRepo = resourceRepo;
        this.auditRepo = auditRepo;
        this.objectStorageService = objectStorageService;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    public List<User> listUsers(String role, String status, int page, int size) {
        return userRepo.list(role, status, size, (page - 1) * size);
    }

    public long countUsers(String role, String status) {
        return userRepo.count(role, status);
    }

    public User createUser(
            String username, String password, String role, String email, String phone) {
        if (userRepo.existsByUsername(username)) throw new ConflictException("用户名已存在");
        UUID id = userRepo.create(username, passwordEncoder.encode(password), email, phone, role);
        return userRepo.findById(id);
    }

    public User patchUser(UUID userId, String role, String status) {
        User existing = userRepo.findById(userId);
        String finalRole = (role == null || role.isBlank()) ? existing.role() : role;
        String finalStatus = (status == null || status.isBlank()) ? existing.status() : status;
        userRepo.patchRoleStatus(userId, finalRole, finalStatus);
        return userRepo.findById(userId);
    }

    public List<AdminResource> listResources(String resourceType, int page, int size) {
        return resourceRepo.list(resourceType, size, (page - 1) * size);
    }

    public long countResources(String resourceType) {
        return resourceRepo.count(resourceType);
    }

    public record DownloadData(String filename, byte[] bytes) {}

    public DownloadData downloadResource(UUID resourceId) {
        // Try lesson plan
        List<String[]> planRows =
                jdbc.query(
                        "select topic,content_md from lesson_plans where id=? and deleted_at is null",
                        (rs, rn) ->
                                new String[] {rs.getString("topic"), rs.getString("content_md")},
                        resourceId);
        if (!planRows.isEmpty()) {
            String[] row = planRows.getFirst();
            return new DownloadData(
                    FilenameUtil.sanitize(row[0]) + ".md", row[1].getBytes(StandardCharsets.UTF_8));
        }

        // Try document
        List<String[]> docRows =
                jdbc.query(
                        "select filename,storage_path from documents where id=? and deleted_at is null",
                        (rs, rn) ->
                                new String[] {
                                    rs.getString("filename"), rs.getString("storage_path")
                                },
                        resourceId);
        if (!docRows.isEmpty()) {
            String[] row = docRows.getFirst();
            return new DownloadData(
                    FilenameUtil.sanitize(row[0]), objectStorageService.download(row[1]));
        }

        // Try question
        List<String[]> qRows =
                jdbc.query(
                        "select content,analysis from questions where id=? and is_active=true",
                        (rs, rn) ->
                                new String[] {rs.getString("content"), rs.getString("analysis")},
                        resourceId);
        if (!qRows.isEmpty()) {
            String[] row = qRows.getFirst();
            String payload = "题干:\n" + row[0] + "\n\n解析:\n" + row[1];
            return new DownloadData(
                    "question-" + resourceId + ".txt", payload.getBytes(StandardCharsets.UTF_8));
        }

        throw new ResourceNotFoundException("资源不存在");
    }

    public List<AuditLog> listAudits(int page, int size) {
        return auditRepo.list(size, (page - 1) * size);
    }

    public long countAudits() {
        return auditRepo.count();
    }

    public DashboardMetrics getDashboardMetrics() {
        return new DashboardMetrics(
                countOf("select count(*) from users where deleted_at is null"),
                countOf("select count(*) from users where role='STUDENT' and deleted_at is null"),
                countOf("select count(*) from users where role='TEACHER' and deleted_at is null"),
                countOf("select count(*) from users where role='ADMIN' and deleted_at is null"),
                countOf("select count(*) from chat_sessions where is_deleted=false"),
                countOf("select count(*) from chat_messages"),
                countOf("select count(*) from exercise_records"),
                countOf("select count(*) from questions where is_active=true"),
                countOf("select count(*) from documents where deleted_at is null"),
                countOf(
                        "select coalesce(sum((result->>'chunks')::bigint),0) from job_runs where job_type='DOCUMENT_INGEST' and status='SUCCEEDED' and jsonb_exists(result,'chunks')"),
                countOf("select count(*) from lesson_plans where deleted_at is null"),
                countOf("select count(*) from ai_question_sessions"));
    }

    private long countOf(String sql) {
        Number val = jdbc.queryForObject(sql, Number.class);
        return val == null ? 0L : val.longValue();
    }
}
