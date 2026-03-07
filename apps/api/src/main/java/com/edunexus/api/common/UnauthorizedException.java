package com.edunexus.api.common;

public class UnauthorizedException extends RuntimeException {
    private final ErrorCode errorCode;

    public UnauthorizedException(String message) {
        this(ErrorCode.AUTH_TOKEN_INVALID, message);
    }

    public UnauthorizedException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
