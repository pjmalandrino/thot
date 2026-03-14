package com.example.contextengine.domain.model;

import java.util.List;

public class StepResult {

    public enum Action { CONTINUE, INTERRUPT }

    private final Action action;
    private final String type;
    private final String message;
    private final List<String> suggestions;
    private final double confidence;

    private StepResult(Action action, String type, String message, List<String> suggestions, double confidence) {
        this.action = action;
        this.type = type;
        this.message = message;
        this.suggestions = suggestions;
        this.confidence = confidence;
    }

    public static StepResult continueProcessing() {
        return new StepResult(Action.CONTINUE, null, null, List.of(), 1.0);
    }

    public static StepResult interrupt(String type, String message, List<String> suggestions, double confidence) {
        return new StepResult(
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
