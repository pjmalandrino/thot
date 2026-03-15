package com.example.contextengine.infrastructure.adapter.in.rest.dto;

import com.example.contextengine.domain.model.ConversationMessage;

import java.util.List;

/**
 * Typed request DTO for all streaming endpoints (think, research, lab).
 * Replaces the unsafe Map<String, Object> previously used.
 */
public record StreamingRequestDto(
    String prompt,
    String documentContext,
    String systemPrompt,
    List<ConversationMessageItem> conversationHistory,
    Boolean webSearchRequested
) {
    public record ConversationMessageItem(String role, String content) {}

    public List<ConversationMessage> toDomainHistory() {
        if (conversationHistory == null) return List.of();
        return conversationHistory.stream()
                .filter(item -> item != null && item.role() != null && item.content() != null)
                .map(item -> new ConversationMessage(item.role(), item.content()))
                .toList();
    }

    public boolean isWebSearchRequestedOrDefault() {
        return webSearchRequested != null && webSearchRequested;
    }
}
