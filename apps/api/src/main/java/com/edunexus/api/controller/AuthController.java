package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthContext;
import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.auth.JwtUtil;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.common.TraceFilter;
import com.edunexus.api.service.DbService;
import com.edunexus.api.service.GovernanceService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final DbService db;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final GovernanceService governance;

    public AuthController(DbService db, PasswordEncoder encoder, JwtUtil jwt, GovernanceService governance) {
        this.db = db;
        this.encoder = encoder;
        this.jwt = jwt;
        this.governance = governance;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterReq req, HttpServletRequest request) {
        if (db.exists("select 1 from users where username=? and deleted_at is null", req.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (!req.role().equals("STUDENT") && !req.role().equals("TEACHER")) {
            throw new IllegalArgumentException("role 仅支持 STUDENT/TEACHER");
        }
        UUID id = db.newId();
        db.update("insert into users(id,username,password_hash,email,phone,role,status) values (?,?,?,?,?,?, 'ACTIVE')",
                id, req.username(), encoder.encode(req.password()), req.email(), req.phone(), req.role());
        governance.audit(id, req.role(), "REGISTER", "USER", id.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", id), trace(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        Map<String, Object> user = db.one("select id,username,password_hash,role,status from users where username=? and deleted_at is null", req.username());
        String pwd = String.valueOf(user.get("password_hash"));
        boolean matched = encoder.matches(req.password(), pwd);
        if (!matched) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "用户名或密码错误", trace(request)));
        }
        String status = String.valueOf(user.get("status"));
        if (!"ACTIVE".equals(status)) {
            return ResponseEntity.status(403).body(ApiResponse.error(403, "账号已禁用", trace(request)));
        }
        String userId = String.valueOf(user.get("id"));
        String username = String.valueOf(user.get("username"));
        String role = String.valueOf(user.get("role"));

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("role", role);
        claims.put("status", status);
        String access = jwt.generateAccessToken(claims, userId);
        String refresh = jwt.generateRefreshToken(userId);
        upsertRefreshToken(UUID.fromString(userId), refresh, false);

        Map<String, Object> data = Map.of(
                "accessToken", access,
                "refreshToken", refresh,
                "user", Map.of("id", userId, "username", username, "role", role)
        );
        governance.audit(UUID.fromString(userId), role, "LOGIN", "USER", userId, trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request) {
        AuthUser user = requireAuth();
        String token = parseBearerToken(request.getHeader("Authorization"));
        if (token != null) {
            try {
                Claims claims = jwt.parse(token);
                revokeAccessToken(claims);
            } catch (Exception ignored) {
                // ignore malformed token on logout, refresh tokens are still revoked
            }
        }
        db.update("update refresh_tokens set revoked_at=now() where user_id=? and revoked_at is null", user.userId());
        governance.audit(user.userId(), user.role(), "LOGOUT", "USER", user.userId().toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refresh(@Valid @RequestBody RefreshReq req, HttpServletRequest request) {
        Claims claims = jwt.parse(req.refreshToken());
        UUID userId = UUID.fromString(claims.getSubject());
        String tokenHash = sha256(req.refreshToken());
        if (!db.exists("select 1 from refresh_tokens where user_id=? and token_hash=? and revoked_at is null and expires_at > now()", userId, tokenHash)) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "refresh token 无效", trace(request)));
        }
        Map<String, Object> user = db.one("select id,username,role,status from users where id=?", userId);
        Map<String, Object> newClaims = Map.of(
                "username", String.valueOf(user.get("username")),
                "role", String.valueOf(user.get("role")),
                "status", String.valueOf(user.get("status"))
        );
        String access = jwt.generateAccessToken(newClaims, String.valueOf(userId));
        String refresh = jwt.generateRefreshToken(String.valueOf(userId));
        upsertRefreshToken(userId, req.refreshToken(), true);
        upsertRefreshToken(userId, refresh, false);
        governance.audit(userId, String.valueOf(user.get("role")), "REFRESH_TOKEN", "USER", userId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("accessToken", access, "refreshToken", refresh), trace(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse> me(HttpServletRequest request) {
        AuthUser user = requireAuth();
        Map<String, Object> row = db.one("select id,username,role,status,created_at from users where id=?", user.userId());
        return ResponseEntity.ok(ApiResponse.ok(row, trace(request)));
    }

    private AuthUser requireAuth() {
        AuthUser user = AuthContext.get();
        if (user == null) throw new SecurityException("未认证");
        return user;
    }

    private String trace(HttpServletRequest request) {
        Object o = request.getAttribute(TraceFilter.TRACE_ID);
        return o == null ? "" : o.toString();
    }

    private void upsertRefreshToken(UUID userId, String token, boolean revoke) {
        String hash = sha256(token);
        if (revoke) {
            db.update("update refresh_tokens set revoked_at=now() where user_id=? and token_hash=? and revoked_at is null", userId, hash);
            return;
        }
        db.update("insert into refresh_tokens(id,user_id,token_hash,expires_at) values (?,?,?,?)",
                db.newId(), userId, hash, Timestamp.from(Instant.now().plus(14, ChronoUnit.DAYS)));
    }

    private void revokeAccessToken(Claims claims) {
        String jti = claims.get("jti", String.class);
        String sub = claims.getSubject();
        java.util.Date exp = claims.getExpiration();
        if (jti == null || jti.isBlank() || sub == null || sub.isBlank() || exp == null) {
            return;
        }
        db.update(
                "insert into access_token_blacklist(id,jti,user_id,expires_at) values (?,?,?,?) on conflict (jti) do nothing",
                db.newId(),
                jti,
                UUID.fromString(sub),
                Timestamp.from(exp.toInstant())
        );
    }

    private String parseBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public record RegisterReq(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 64) String password,
            @Email String email,
            @Size(max = 20) String phone,
            @NotBlank @Pattern(regexp = "STUDENT|TEACHER") String role
    ) {}

    public record LoginReq(@NotBlank String username, @NotBlank String password) {}
    public record RefreshReq(@NotBlank String refreshToken) {}
}
