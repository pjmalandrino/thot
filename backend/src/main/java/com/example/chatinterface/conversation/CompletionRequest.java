package com.example.chatinterface.conversation;

public class CompletionRequest {

    private String prompt;
    private boolean webSearch;
    private Long modelId;   // null = fallback sur le premier modèle enabled
    private String clarificationContext;  // réponse à une demande de clarification
    private boolean driveSearchEnabled;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public boolean isWebSearch() { return webSearch; }
    public void setWebSearch(boolean webSearch) { this.webSearch = webSearch; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public String getClarificationContext() { return clarificationContext; }
    public void setClarificationContext(String clarificationContext) { this.clarificationContext = clarificationContext; }

    public boolean isDriveSearchEnabled() { return driveSearchEnabled; }
    public void setDriveSearchEnabled(boolean driveSearchEnabled) { this.driveSearchEnabled = driveSearchEnabled; }
}
