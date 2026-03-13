package com.example.chatinterface.context;

import java.util.List;

/**
 * DTO reponse pour l'endpoint POST /api/context/analyze.
 *
 * Deux formes :
 * - { "status": "continue", "confidence": 1.0 }
 * - { "status": "clarification_needed", "message": "...", "suggestions": [...], "confidence": 0.9 }
 */
public class ContextResponse {

    private String status;
    private String message;
    private List<String> suggestions;
    private Double confidence;

    private ContextResponse() {}

    public static ContextResponse from(ContextStepResult result) {
        ContextResponse response = new ContextResponse();
        response.confidence = result.getConfidence();
        if (result.shouldContinue()) {
            response.status = "continue";
            response.message = null;
            response.suggestions = null;
        } else {
            response.status = result.getType();
            response.message = result.getMessage();
            response.suggestions = result.getSuggestions();
        }
        return response;
    }

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public List<String> getSuggestions() { return suggestions; }
    public Double getConfidence() { return confidence; }
}
