package com.example.chatinterface.shared.exception;

/**
 * Thrown when a request violates a business validation rule.
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
