package com.edunexus.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long accessMinutes;
    private final long refreshDays;

    public JwtUtil(@Value("${app.jwt-secret}") String secret,
                   @Value("${app.jwt-expires-in:15m}") String access,
                   @Value("${app.refresh-token-expires-in:14d}") String refresh) {
        String fixed = secret.length() < 32 ? (secret + "-edunexus-secret-padding-for-jwt") : secret;
        this.key = Keys.hmacShaKeyFor(fixed.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = parseMinutes(access);
        this.refreshDays = parseDays(refresh);
    }

    public String generateAccessToken(Map<String, Object> claims, String subject) {
        Instant now = Instant.now();
        Map<String, Object> payload = new HashMap<>(claims);
        payload.putIfAbsent("jti", UUID.randomUUID().toString());
        return Jwts.builder()
                .claims(payload)
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshDays, ChronoUnit.DAYS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private long parseMinutes(String s) {
        if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1));
        if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60;
        return 15;
    }

    private long parseDays(String s) {
        if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1));
        return 14;
    }
}
