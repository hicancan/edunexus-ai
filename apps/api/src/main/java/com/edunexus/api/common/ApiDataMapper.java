package com.edunexus.api.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiDataMapper {
    private ApiDataMapper() {
    }

    public static Map<String, Object> pagedData(List<?> content, int page, int size, long totalElements) {
        long totalPages = totalElements == 0 ? 0 : (long) Math.ceil((double) totalElements / (double) size);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("page", page);
        out.put("size", size);
        out.put("totalElements", totalElements);
        out.put("totalPages", totalPages);
        return out;
    }

    public static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    public static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    public static double asDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    public static boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static String asIsoTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().toString();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant().toString();
        }
        return String.valueOf(value);
    }

    public static Object parseJsonValue(Object value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return value;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        if (!(raw.startsWith("{") || raw.startsWith("["))) {
            return value;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception ex) {
            return value;
        }
    }

    public static Map<String, String> parseStringMap(Object value, ObjectMapper objectMapper) {
        Object parsed = parseJsonValue(value, objectMapper);
        if (!(parsed instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return out;
    }

    public static List<String> parseStringList(Object value, ObjectMapper objectMapper) {
        Object parsed = parseJsonValue(value, objectMapper);
        if (!(parsed instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream().map(String::valueOf).toList();
    }

    public static List<Map<String, Object>> parseObjectList(Object value, ObjectMapper objectMapper) {
        Object parsed = parseJsonValue(value, objectMapper);
        if (!(parsed instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object item : list) {
            try {
                out.add(objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {
                }));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry
            }
        }
        return out;
    }
}
