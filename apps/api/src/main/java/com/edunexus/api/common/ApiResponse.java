package com.edunexus.api.common;

import java.time.Instant;

public record ApiResponse(
        int code,
        String message,
        Object data,
        String traceId,
        String timestamp
) {
    public static ApiResponse ok(Object data, String traceId) {
        return new ApiResponse(200, "success", data, traceId, Instant.now().toString());
    }

    public static ApiResponse created(Object data, String traceId) {
        return new ApiResponse(201, "success", data, traceId, Instant.now().toString());
    }

    public static ApiResponse accepted(Object data, String traceId) {
        return new ApiResponse(202, "success", data, traceId, Instant.now().toString());
    }

    public static ApiResponse error(int code, String message, String traceId) {
        return new ApiResponse(code, message, null, traceId, Instant.now().toString());
    }
}
