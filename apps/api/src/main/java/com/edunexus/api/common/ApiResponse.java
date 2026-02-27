package com.edunexus.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

public record ApiResponse(
        int code,
        String message,
        Object data,
        String traceId,
        String timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode
) {
    private static ApiResponse of(int code, String message, Object data, String traceId, String errorCode) {
        return new ApiResponse(code, message, data, traceId, Instant.now().toString(), errorCode);
    }

    public static ApiResponse ok(Object data, String traceId) {
        return of(200, "success", data, traceId, null);
    }

    public static ApiResponse created(Object data, String traceId) {
        return of(201, "success", data, traceId, null);
    }

    public static ApiResponse accepted(Object data, String traceId) {
        return of(202, "success", data, traceId, null);
    }

    public static ApiResponse error(ErrorCode errorCode, String message, String traceId) {
        String resolvedMessage = message == null || message.isBlank() ? errorCode.defaultMessage() : message;
        return of(errorCode.httpStatus(), resolvedMessage, null, traceId, errorCode.name());
    }

    public static ApiResponse error(ErrorCode errorCode, String traceId) {
        return of(errorCode.httpStatus(), errorCode.defaultMessage(), null, traceId, errorCode.name());
    }
}
