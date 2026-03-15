package com.example.chatinterface.shared.exception;

/**
 * Thrown when an internal service or external dependency fails.
 * Mapped to HTTP 500 by {@link GlobalExceptionHandler}.
 */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
