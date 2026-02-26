package com.edunexus.api.common;

public class DependencyException extends RuntimeException {
    public DependencyException(String message) {
        super(message);
    }

    public DependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
