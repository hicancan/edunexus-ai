package com.edunexus.api.common;

public class DependencyException extends RuntimeException {
    private final ErrorCode errorCode;

    public DependencyException(String message) {
        this(ErrorCode.SYSTEM_DEPENDENCY, message, null);
    }

    public DependencyException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public DependencyException(String message, Throwable cause) {
        this(ErrorCode.SYSTEM_DEPENDENCY, message, cause);
    }

    public DependencyException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
