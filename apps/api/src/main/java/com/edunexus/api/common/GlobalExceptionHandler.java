package com.edunexus.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse> handleSecurity(SecurityException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField() + " " + e.getDefaultMessage()).orElse("参数错误");
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message, trace(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleAny(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "internal error: " + ex.getMessage(), trace(request)));
    }

    private String trace(HttpServletRequest request) {
        Object t = request.getAttribute(TraceFilter.TRACE_ID);
        return t == null ? "" : t.toString();
    }
}
