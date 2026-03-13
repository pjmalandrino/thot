package com.example.chatinterface.dto;

public class CompletionRequest {

    private String prompt;
    private boolean webSearch;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public boolean isWebSearch() { return webSearch; }
    public void setWebSearch(boolean webSearch) { this.webSearch = webSearch; }
}
