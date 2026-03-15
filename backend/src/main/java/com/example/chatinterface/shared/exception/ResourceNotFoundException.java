package com.example.chatinterface.shared.exception;

/**
 * Thrown when a requested resource (Conversation, Thotspace, Model, etc.) does not exist.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " not found: " + id);
    }
}
