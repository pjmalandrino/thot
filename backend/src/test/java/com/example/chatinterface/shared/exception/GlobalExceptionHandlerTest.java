package com.example.chatinterface.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResourceNotFoundException → 404 NOT_FOUND")
    void handleNotFound() {
        ProblemDetail result = handler.handleNotFound(
                new ResourceNotFoundException("Conversation", 42L));

        assertThat(result.getStatus()).isEqualTo(404);
        assertThat(result.getDetail()).contains("Conversation").contains("42");
    }

    @Test
    @DisplayName("ValidationException → 400 BAD_REQUEST")
    void handleValidation() {
        ProblemDetail result = handler.handleValidation(
                new ValidationException("Le champ email est requis"));

        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getDetail()).isEqualTo("Le champ email est requis");
    }

    @Test
    @DisplayName("ServiceException → 500 INTERNAL_SERVER_ERROR")
    void handleService() {
        ProblemDetail result = handler.handleService(
                new ServiceException("Erreur de connexion au LLM"));

        assertThat(result.getStatus()).isEqualTo(500);
        assertThat(result.getDetail()).isEqualTo("Erreur de connexion au LLM");
    }

    @Test
    @DisplayName("IllegalStateException → 409 CONFLICT")
    void handleIllegalState() {
        ProblemDetail result = handler.handleIllegalState(
                new IllegalStateException("Cannot delete the default thotspace"));

        assertThat(result.getStatus()).isEqualTo(409);
        assertThat(result.getDetail()).isEqualTo("Cannot delete the default thotspace");
    }
}
