package com.example.chatinterface.contextengine;

import java.util.List;

public class ContextEngineRequest {

    private String prompt;
    private List<ConversationMessageDto> conversationHistory;
    private String documentContext;
    private boolean webSearchRequested;

    public record ConversationMessageDto(String role, String content) {}

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public List<ConversationMessageDto> getConversationHistory() { return conversationHistory; }
    public void setConversationHistory(List<ConversationMessageDto> conversationHistory) { this.conversationHistory = conversationHistory; }

    public String getDocumentContext() { return documentContext; }
    public void setDocumentContext(String documentContext) { this.documentContext = documentContext; }

    public boolean isWebSearchRequested() { return webSearchRequested; }
    public void setWebSearchRequested(boolean webSearchRequested) { this.webSearchRequested = webSearchRequested; }
}
