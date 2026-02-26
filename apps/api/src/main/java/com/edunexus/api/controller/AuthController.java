package com.edunexus.api.controller;

import com.edunexus.api.auth.AuthUser;
import com.edunexus.api.auth.JwtUtil;
import com.edunexus.api.common.ApiDataMapper;
import com.edunexus.api.common.ApiResponse;
import com.edunexus.api.common.ConflictException;
import com.edunexus.api.common.CryptoUtil;
import com.edunexus.api.common.UnauthorizedException;
import com.edunexus.api.service.DbService;
import com.edunexus.api.service.GovernanceService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements ControllerSupport {
    private final DbService db;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final GovernanceService governance;

    public AuthController(DbService db, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
            GovernanceService governance) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.governance = governance;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterReq req, HttpServletRequest request) {
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
                req.role());

        Map<String, Object> user = db.one("select * from users where id=?", userId);
        governance.audit(userId, req.role(), "REGISTER", "USER", userId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(toUserVo(user), trace(request)));
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        Map<String, Object> user = db.oneOrNull("select * from users where username=? and deleted_at is null",
                req.username());
        if (user == null) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        String passwordHash = String.valueOf(user.get("password_hash"));
        if (!passwordEncoder.matches(req.password(), passwordHash)) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        String status = String.valueOf(user.get("status"));
        if (!"ACTIVE".equals(status)) {
            throw new SecurityException("账号已禁用");
        }

        UUID userId = UUID.fromString(String.valueOf(user.get("id")));
        Map<String, String> tokenPair = issueTokenPair(user);
        upsertRefreshToken(userId, tokenPair.get("refreshToken"), false);

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", tokenPair.get("accessToken"));
        data.put("refreshToken", tokenPair.get("refreshToken"));
        data.put("user", toUserVo(user));
        governance.audit(userId, String.valueOf(user.get("role")), "LOGIN", "USER", userId.toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(data, trace(request)));
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<ApiResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AuthUser user = currentUser();
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String accessToken = authorization.substring(7);
            try {
                Claims claims = jwtUtil.parse(accessToken);
                revokeAccessToken(claims);
            } catch (Exception ignored) {
                // Logout should be idempotent even with an invalid access token.
            }
        }
        // M-03: 精确吊销当前 session 的 refresh token，而非批量吊销所有 session
        String currentRefreshToken = request.getHeader("X-Refresh-Token");
        if (currentRefreshToken != null && !currentRefreshToken.isBlank()) {
            String tokenHash = CryptoUtil.sha256(currentRefreshToken);
            db.update(
                    "update refresh_tokens set revoked_at=now() where user_id=? and token_hash=? and revoked_at is null",
                    user.userId(), tokenHash);
        } else {
            // 兜底：无法定位当前 session 时仍批量吊销
            db.update("update refresh_tokens set revoked_at=now() where user_id=? and revoked_at is null",
                    user.userId());
        }
        governance.audit(user.userId(), user.role(), "LOGOUT", "USER", user.userId().toString(), trace(request));
        return ResponseEntity.ok(ApiResponse.ok(null, trace(request)));
    }

    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<ApiResponse> refresh(@Valid @RequestBody RefreshReq req, HttpServletRequest request) {
        Claims claims;
        try {
            claims = jwtUtil.parse(req.refreshToken());
        } catch (Exception ex) {
            throw new UnauthorizedException("refresh token 无效");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        String tokenHash = CryptoUtil.sha256(req.refreshToken());

        Map<String, Object> tokenRow = db.oneOrNull(
                "select id from refresh_tokens where user_id=? and token_hash=? and revoked_at is null and expires_at > now()",
                userId,
                tokenHash);
        if (tokenRow == null) {
            throw new UnauthorizedException("refresh token 无效");
        }

        Map<String, Object> user = db.oneOrNull("select * from users where id=? and deleted_at is null", userId);
        if (user == null) {
            throw new UnauthorizedException("refresh token 无效");
        }
        if (!"ACTIVE".equals(String.valueOf(user.get("status")))) {
            throw new SecurityException("账号已禁用");
        }

        upsertRefreshToken(userId, req.refreshToken(), true);
        Map<String, String> tokenPair = issueTokenPair(user);
        upsertRefreshToken(userId, tokenPair.get("refreshToken"), false);

        governance.audit(userId, String.valueOf(user.get("role")), "REFRESH_TOKEN", "USER", userId.toString(),
                trace(request));
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "accessToken", tokenPair.get("accessToken"),
                "refreshToken", tokenPair.get("refreshToken")), trace(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse> me(HttpServletRequest request) {
        AuthUser authUser = currentUser();
        Map<String, Object> user = db.one("select * from users where id=? and deleted_at is null", authUser.userId());
        return ResponseEntity.ok(ApiResponse.ok(toUserVo(user), trace(request)));
    }

    private Map<String, String> issueTokenPair(Map<String, Object> user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", String.valueOf(user.get("username")));
        claims.put("role", String.valueOf(user.get("role")));
        claims.put("status", String.valueOf(user.get("status")));

        String subject = String.valueOf(user.get("id"));
        String accessToken = jwtUtil.generateAccessToken(claims, subject);
        String refreshToken = jwtUtil.generateRefreshToken(subject);

        Map<String, String> pair = new HashMap<>();
        pair.put("accessToken", accessToken);
        pair.put("refreshToken", refreshToken);
        return pair;
    }

    private Map<String, Object> toUserVo(Map<String, Object> row) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", String.valueOf(row.get("id")));
        user.put("username", String.valueOf(row.get("username")));
        user.put("role", String.valueOf(row.get("role")));
        user.put("status", String.valueOf(row.get("status")));
        user.put("email", ApiDataMapper.asString(row.get("email")));
        user.put("phone", ApiDataMapper.asString(row.get("phone")));
        user.put("createdAt", ApiDataMapper.asIsoTime(row.get("created_at")));
        user.put("updatedAt", ApiDataMapper.asIsoTime(row.get("updated_at")));
        return user;
    }

    private void revokeAccessToken(Claims claims) {
        String jti = claims.get("jti", String.class);
        String subject = claims.getSubject();
        Instant expiresAt = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
        if (jti == null || jti.isBlank() || subject == null || subject.isBlank() || expiresAt == null) {
            return;
        }
        db.update(
                "insert into access_token_blacklist(id,jti,user_id,expires_at) values (?,?,?,?) on conflict (jti) do nothing",
                db.newId(),
                jti,
                UUID.fromString(subject),
                Timestamp.from(expiresAt));
    }

    private void upsertRefreshToken(UUID userId, String token, boolean revoke) {
        String hash = CryptoUtil.sha256(token);
        if (revoke) {
            db.update(
                    "update refresh_tokens set revoked_at=now() where user_id=? and token_hash=? and revoked_at is null",
                    userId, hash);
            return;
        }
        Instant expiresAt;
        try {
            expiresAt = jwtUtil.parse(token).getExpiration().toInstant();
        } catch (Exception ex) {
            expiresAt = Instant.now().plus(14, ChronoUnit.DAYS);
        }
        db.update(
                "insert into refresh_tokens(id,user_id,token_hash,expires_at) values (?,?,?,?)",
                db.newId(),
                userId,
                hash,
                Timestamp.from(expiresAt));
    }

    public record RegisterReq(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 64) String password,
            @Email String email,
            @Size(max = 20) String phone,
            @NotBlank @Pattern(regexp = "STUDENT|TEACHER") String role) {
    }

    public record LoginReq(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshReq(@NotBlank String refreshToken) {
    }
}
