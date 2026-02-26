package com.edunexus.api.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "test-secret-key-for-edunexus-with-enough-length-123456",
            "15m",
            "14d"
    );

    @Test
    void generateRefreshToken_shouldRotateByJti() {
        String userId = "00000000-0000-0000-0000-000000000003";

        String first = jwtUtil.generateRefreshToken(userId);
        String second = jwtUtil.generateRefreshToken(userId);

        assertNotEquals(first, second);

        Claims firstClaims = jwtUtil.parse(first);
        Claims secondClaims = jwtUtil.parse(second);
        assertNotNull(firstClaims.get("jti", String.class));
        assertNotNull(secondClaims.get("jti", String.class));
        assertNotEquals(firstClaims.get("jti", String.class), secondClaims.get("jti", String.class));
    }

    @Test
    void generateAccessToken_shouldContainCoreClaims() {
        String token = jwtUtil.generateAccessToken(
                Map.of("username", "student01", "role", "STUDENT", "status", "ACTIVE"),
                "00000000-0000-0000-0000-000000000003"
        );

        Claims claims = jwtUtil.parse(token);
        assertEquals("00000000-0000-0000-0000-000000000003", claims.getSubject());
        assertEquals("student01", claims.get("username", String.class));
        assertEquals("STUDENT", claims.get("role", String.class));
        assertEquals("ACTIVE", claims.get("status", String.class));
        assertNotNull(claims.get("jti", String.class));
    }
}
