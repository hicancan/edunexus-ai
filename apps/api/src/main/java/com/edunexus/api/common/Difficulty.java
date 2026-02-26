package com.edunexus.api.common;

/**
 * 难度枚举 — 集中定义替代散落在各 Controller 中的字符串校验 (L-03)。
 */
public enum Difficulty {
    EASY, MEDIUM, HARD;

    public static Difficulty fromString(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("difficulty 仅支持 EASY/MEDIUM/HARD");
        }
    }
}
