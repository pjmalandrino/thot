package com.example.chatinterface.conversation;

public class CompletionRequest {

    private String prompt;
    private boolean webSearch;
    private Long modelId;   // null = fallback sur le premier modèle enabled

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public boolean isWebSearch() { return webSearch; }
    public void setWebSearch(boolean webSearch) { this.webSearch = webSearch; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
}
