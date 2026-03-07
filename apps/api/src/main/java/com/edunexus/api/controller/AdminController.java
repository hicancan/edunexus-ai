package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.service.AdminService;
import com.edunexus.api.service.GovernanceService;
import com.edunexus.api.service.VoMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController implements ControllerSupport {

    private final AdminService adminService;
    private final GovernanceService governance;
    private final VoMapper voMapper;

    public AdminController(
            AdminService adminService, GovernanceService governance, VoMapper voMapper) {
        this.adminService = adminService;
        this.governance = governance;
        this.voMapper = voMapper;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse> listUsers(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("ADMIN");
        if (role != null
                && !role.isBlank()
                && !"STUDENT".equals(role)
                && !"TEACHER".equals(role)
                && !"ADMIN".equals(role))
            throw new IllegalArgumentException("role 仅支持 STUDENT/TEACHER/ADMIN");
        if (status != null
                && !status.isBlank()
                && !"ACTIVE".equals(status)
                && !"DISABLED".equals(status))
            throw new IllegalArgumentException("status 仅支持 ACTIVE/DISABLED");

        var users = adminService.listUsers(role, status, page, size);
        long total = adminService.countUsers(role, status);
        var content = users.stream().map(voMapper::toUserVo).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse> createUser(
            @Valid @RequestBody AdminUserCreateReq req, HttpServletRequest request) {
        requireRole("ADMIN");
        var user =
                adminService.createUser(
                        req.username(), req.password(), req.role(), req.email(), req.phone());
        audit("CREATE_USER", "USER", user.id().toString(), request);
        return ResponseEntity.status(201)
                .body(ApiResponse.created(voMapper.toUserVo(user), trace(request)));
    }

    @PatchMapping("/users/{userId}")
    public ResponseEntity<ApiResponse> patchUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AdminUserPatchReq req,
            HttpServletRequest request) {
        requireRole("ADMIN");
        var user = adminService.patchUser(userId, req.role(), req.status());
        audit("PATCH_USER", "USER", userId.toString(), request);
        return ResponseEntity.ok(ApiResponse.ok(voMapper.toUserVo(user), trace(request)));
    }

    @GetMapping("/resources")
    public ResponseEntity<ApiResponse> listResources(
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("ADMIN");
        if (resourceType != null
                && !resourceType.isBlank()
                && !"LESSON_PLAN".equals(resourceType)
                && !"QUESTION".equals(resourceType)
                && !"DOCUMENT".equals(resourceType))
            throw new IllegalArgumentException("resourceType 仅支持 LESSON_PLAN/QUESTION/DOCUMENT");

        var resources = adminService.listResources(resourceType, page, size);
        long total = adminService.countResources(resourceType);
        var content = resources.stream().map(voMapper::toAdminResourceVo).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @GetMapping("/resources/{resourceId}/download")
    public ResponseEntity<byte[]> downloadResource(
            @PathVariable("resourceId") UUID resourceId, HttpServletRequest request) {
        requireRole("ADMIN");
        var data = adminService.downloadResource(resourceId);
        audit("DOWNLOAD_RESOURCE", "RESOURCE", resourceId.toString(), request);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + data.filename() + "\"")
                .header("X-Request-Id", trace(request))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data.bytes());
    }

    @GetMapping("/audits")
    public ResponseEntity<ApiResponse> listAudits(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        requireRole("ADMIN");
        var audits = adminService.listAudits(page, size);
        long total = adminService.countAudits();
        var content = audits.stream().map(voMapper::toAuditLogVo).toList();
        return ResponseEntity.ok(
                ApiResponse.ok(
                        ApiDataMapper.pagedData(content, page, size, total), trace(request)));
    }

    @GetMapping("/dashboard/metrics")
    public ResponseEntity<ApiResponse> dashboardMetrics(HttpServletRequest request) {
        requireRole("ADMIN");
        var metrics = adminService.getDashboardMetrics();
        return ResponseEntity.ok(
                ApiResponse.ok(voMapper.toDashboardMetricsVo(metrics), trace(request)));
    }

    private void audit(
            String action, String resourceType, String resourceId, HttpServletRequest request) {
        AuthUser user = currentUser();
        governance.audit(
                user.userId(), user.role(), action, resourceType, resourceId, trace(request));
    }

    public record AdminUserCreateReq(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank @Pattern(regexp = "STUDENT|TEACHER|ADMIN") String role,
            @Email String email,
            String phone) {}

    public record AdminUserPatchReq(
            @Pattern(regexp = "STUDENT|TEACHER|ADMIN") String role,
            @Pattern(regexp = "ACTIVE|DISABLED") String status) {}
}
