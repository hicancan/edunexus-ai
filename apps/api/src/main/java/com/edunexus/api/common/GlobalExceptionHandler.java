package com.edunexus.api.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_FIELD, ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_PARAM, trace(request)));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.errorCode().httpStatus())
                .body(ApiResponse.error(ex.errorCode(), ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.errorCode().httpStatus())
                .body(ApiResponse.error(ex.errorCode(), ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse> handleSecurity(SecurityException ex, HttpServletRequest request) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        ErrorCode code;
        if (message.contains("非资源归属者")) {
            code = ErrorCode.PERMISSION_OWNERSHIP;
        } else if (message.contains("账号已禁用")) {
            code = ErrorCode.AUTH_ACCOUNT_DISABLED;
        } else if (message.contains("未认证")) {
            code = ErrorCode.AUTH_TOKEN_INVALID;
        } else {
            code = ErrorCode.PERMISSION_DENIED;
        }
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code, ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.RESOURCE_CONFLICT, ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(DependencyException.class)
    public ResponseEntity<ApiResponse> handleDependency(DependencyException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.errorCode().httpStatus())
                .body(ApiResponse.error(ex.errorCode(), ex.getMessage(), trace(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("参数错误");
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_FIELD, message, trace(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .orElse("参数错误");
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_PARAM, message, trace(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleAny(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception, traceId={}", trace(request), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.SYSTEM_INTERNAL, trace(request)));
    }

    private String trace(HttpServletRequest request) {
        Object t = request.getAttribute(TraceFilter.TRACE_ID);
        return t == null ? "" : t.toString();
    }
}
