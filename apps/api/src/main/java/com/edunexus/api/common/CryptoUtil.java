package com.edunexus.api.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 公共加密工具类 — 消除 AuthController / TeacherController / GovernanceService 中的 sha256 重复代码。
 */
public final class CryptoUtil {
    private CryptoUtil() {}

    public static String sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
