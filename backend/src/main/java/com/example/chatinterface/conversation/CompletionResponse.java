package com.example.chatinterface.conversation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class CompletionResponse {

    private Long id;
    private String prompt;
    private String response;
    private LocalDateTime createdAt;
    private List<SourceInfo> sources;

    // Pipeline metadata
    private String status;                      // "continue" | "clarification_needed"
    private String clarificationMessage;
    private List<String> suggestions;
    private String rewrittenQuery;
    private boolean autoWebSearchTriggered;
    private Map<String, Integer> tokenUsage;

    public static CompletionResponse from(LlmInteraction interaction) {
        CompletionResponse r = new CompletionResponse();
        r.id = interaction.getId();
        r.prompt = interaction.getPrompt();
        r.response = interaction.getResponse();
        r.createdAt = interaction.getCreatedAt();
        r.sources = interaction.getSources();
        r.status = "continue";
        return r;
    }

    public static CompletionResponse clarification(String prompt, String message,
                                                    List<String> suggestions, Double confidence) {
        CompletionResponse r = new CompletionResponse();
        r.prompt = prompt;
        r.status = "clarification_needed";
        r.clarificationMessage = message;
        r.suggestions = suggestions;
        r.createdAt = LocalDateTime.now();
        return r;
    }

    // Pipeline metadata setters (builder-style)
    public CompletionResponse withPipelineMetadata(String rewrittenQuery,
                                                    boolean autoWebSearchTriggered,
                                                    Map<String, Integer> tokenUsage) {
        this.rewrittenQuery = rewrittenQuery;
        this.autoWebSearchTriggered = autoWebSearchTriggered;
        this.tokenUsage = tokenUsage;
        return this;
    }

    public Long getId() { return id; }
    public String getPrompt() { return prompt; }
    public String getResponse() { return response; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<SourceInfo> getSources() { return sources; }
    public String getStatus() { return status; }
    public String getClarificationMessage() { return clarificationMessage; }
    public List<String> getSuggestions() { return suggestions; }
    public String getRewrittenQuery() { return rewrittenQuery; }
    public boolean isAutoWebSearchTriggered() { return autoWebSearchTriggered; }
    public Map<String, Integer> getTokenUsage() { return tokenUsage; }
}
