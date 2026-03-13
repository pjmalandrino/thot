package com.example.chatinterface.context;

import java.util.List;

/**
 * Resultat d'un step du pipeline de context engineering.
 * Immutable value object avec factory methods.
 * Inclut un score de confiance (0.0–1.0) pour le seuillage.
 */
public class ContextStepResult {

    public enum Action { CONTINUE, INTERRUPT }

    private final Action action;
    private final String type;
    private final String message;
    private final List<String> suggestions;
    private final double confidence;

    private ContextStepResult(Action action, String type, String message, List<String> suggestions, double confidence) {
        this.action = action;
        this.type = type;
        this.message = message;
        this.suggestions = suggestions;
        this.confidence = confidence;
    }

    public static ContextStepResult continueProcessing() {
        return new ContextStepResult(Action.CONTINUE, null, null, List.of(), 1.0);
    }

    public static ContextStepResult interrupt(String type, String message, List<String> suggestions, double confidence) {
        return new ContextStepResult(
                Action.INTERRUPT, type, message,
                suggestions != null ? suggestions : List.of(),
                confidence);
    }

    public Action getAction() { return action; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public List<String> getSuggestions() { return suggestions; }
    public double getConfidence() { return confidence; }

    public boolean shouldContinue() { return action == Action.CONTINUE; }
}
