package com.example.contextengine.infrastructure.adapter.in.rest.dto;

import com.example.contextengine.domain.model.ConversationMessage;

import java.util.List;

public class ContextRequestDto {

    private String prompt;
    private List<ConversationMessageDto> conversationHistory;
    private String documentContext;
    private boolean webSearchRequested;

    public record ConversationMessageDto(String role, String content) {
        public ConversationMessage toDomain() {
            return new ConversationMessage(role, content);
        }
    }

    public List<ConversationMessage> toDomainHistory() {
        if (conversationHistory == null) return List.of();
        return conversationHistory.stream().map(ConversationMessageDto::toDomain).toList();
    }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public List<ConversationMessageDto> getConversationHistory() { return conversationHistory; }
    public void setConversationHistory(List<ConversationMessageDto> conversationHistory) { this.conversationHistory = conversationHistory; }

    public String getDocumentContext() { return documentContext; }
    public void setDocumentContext(String documentContext) { this.documentContext = documentContext; }

    public boolean isWebSearchRequested() { return webSearchRequested; }
    public void setWebSearchRequested(boolean webSearchRequested) { this.webSearchRequested = webSearchRequested; }
}
