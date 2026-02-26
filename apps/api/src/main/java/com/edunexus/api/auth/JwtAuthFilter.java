package com.edunexus.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Instant;
import java.util.HashMap;
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
            "/actuator/health", "/api/v1/teacher/plans/shared"
    );

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
            writeError(request, response, 401, "未认证");
            return;
        }
        String token = auth.substring(7);
        try {
            Claims claims = jwtUtil.parse(token);
            UUID userId = UUID.fromString(claims.getSubject());
            if (isAccessTokenRevoked(claims)) {
                writeError(request, response, 401, "token 已失效");
                return;
            }
            if (!isUserActive(userId)) {
                writeError(request, response, 403, "账号已禁用");
                return;
            }
            AuthContext.set(new AuthUser(
                    userId,
                    claims.get("username", String.class),
                    claims.get("role", String.class),
                    claims.get("status", String.class)
            ));
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            writeError(request, response, 401, "未认证");
        } finally {
            AuthContext.clear();
        }
    }

    private boolean isPublic(String path, String method) {
        if (HttpMethod.OPTIONS.matches(method)) return true;
        if (path.startsWith("/api/v1/teacher/plans/shared/")) return true;
        return PUBLIC_PATHS.contains(path);
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

    private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String message) throws IOException {
        String traceId = request.getHeader("X-Request-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Request-Id", traceId);
        Map<String, Object> body = new HashMap<>();
        body.put("code", status);
        body.put("message", message);
        body.put("data", null);
        body.put("traceId", traceId);
        body.put("timestamp", Instant.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
