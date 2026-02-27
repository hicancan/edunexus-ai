package com.edunexus.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.common.ErrorCode;
import com.edunexus.api.common.TraceFilter;
import com.edunexus.api.service.DbService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final DbService db;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh",
            "/actuator/health", "/api/v1/teacher/plans/shared");
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/v1/teacher/plans/shared/",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html");

    /**
     * S-02: URL 前缀角色网关 — 自动强制 /student/ → STUDENT, /teacher/ → TEACHER, /admin/ →
     * ADMIN
     */
    private static final Map<String, String> PATH_ROLE_MAP = Map.of(
            "/api/v1/student/", "STUDENT",
            "/api/v1/teacher/", "TEACHER",
            "/api/v1/admin/", "ADMIN");

    public JwtAuthFilter(JwtUtil jwtUtil, DbService db) {
        this.jwtUtil = jwtUtil;
        this.db = db;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublic(path, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeError(request, response, 401, ErrorCode.AUTH_TOKEN_INVALID);
            return;
        }
        String token = auth.substring(7);
        try {
            Claims claims = jwtUtil.parse(token);
            UUID userId = UUID.fromString(claims.getSubject());
            if (isAccessTokenRevoked(claims)) {
                writeError(request, response, 401, ErrorCode.AUTH_TOKEN_EXPIRED);
                return;
            }
            if (!isUserActive(userId)) {
                writeError(request, response, 403, ErrorCode.AUTH_ACCOUNT_DISABLED);
                return;
            }
            String userRole = claims.get("role", String.class);

            // S-02: 基于 URL 前缀的角色网关校验
            String requiredRole = resolveRequiredRole(path);
            if (requiredRole != null && !requiredRole.equals(userRole)) {
                writeError(request, response, 403, ErrorCode.PERMISSION_DENIED);
                return;
            }

            AuthContext.set(new AuthUser(
                    userId,
                    claims.get("username", String.class),
                    userRole,
                    claims.get("status", String.class)));
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            writeError(request, response, 401, ErrorCode.AUTH_TOKEN_INVALID);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean isPublic(String path, String method) {
        if (HttpMethod.OPTIONS.matches(method))
            return true;
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return PUBLIC_PATHS.contains(path);
    }

    private String resolveRequiredRole(String path) {
        for (Map.Entry<String, String> entry : PATH_ROLE_MAP.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isAccessTokenRevoked(Claims claims) {
        String jti = claims.get("jti", String.class);
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return db.exists("select 1 from access_token_blacklist where jti=? and expires_at > now()", jti);
    }

    private boolean isUserActive(UUID userId) {
        return db.exists("select 1 from users where id=? and status='ACTIVE' and deleted_at is null", userId);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, int status, ErrorCode errorCode)
            throws IOException {
        String traceId = request.getAttribute(TraceFilter.TRACE_ID) == null
                ? null
                : String.valueOf(request.getAttribute(TraceFilter.TRACE_ID));
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Request-Id");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Trace-Id");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Request-Id", traceId);
        response.setHeader("X-Trace-Id", traceId);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode, traceId)));
    }
}
