package com.example.chatinterface.dto;

import com.example.chatinterface.model.LlmInteraction;
import com.example.chatinterface.model.SourceInfo;

import java.time.LocalDateTime;
import java.util.List;

public class CompletionResponse {

    private Long id;
    private String prompt;
    private String response;
    private LocalDateTime createdAt;
    private List<SourceInfo> sources;

    /**
     * Factory unique : lit les sources directement depuis l'entité persistée.
     */
    public static CompletionResponse from(LlmInteraction interaction) {
        CompletionResponse r = new CompletionResponse();
        r.id = interaction.getId();
        r.prompt = interaction.getPrompt();
        r.response = interaction.getResponse();
        r.createdAt = interaction.getCreatedAt();
        r.sources = interaction.getSources();
        return r;
    }

    public Long getId() { return id; }
    public String getPrompt() { return prompt; }
    public String getResponse() { return response; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<SourceInfo> getSources() { return sources; }
}
