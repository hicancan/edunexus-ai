package com.edunexus.api.common;

public class ForbiddenException extends RuntimeException {
    private final ErrorCode errorCode;

    public ForbiddenException(String message) {
        this(ErrorCode.PERMISSION_DENIED, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
