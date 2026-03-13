package com.example.chatinterface.admin;

import com.example.chatinterface.llm.LlmProvider;
import com.example.chatinterface.llm.LlmProviderType;

import java.time.LocalDateTime;

public class ProviderResponse {

    private Long id;
    private String name;
    private LlmProviderType type;
    private String baseUrl;
    private String apiKeyMasked;   // "****" si présente, null sinon
    private boolean enabled;
    private LocalDateTime createdAt;

    public static ProviderResponse from(LlmProvider p) {
        ProviderResponse r = new ProviderResponse();
        r.id = p.getId();
        r.name = p.getName();
        r.type = p.getType();
        r.baseUrl = p.getBaseUrl();
        r.apiKeyMasked = (p.getApiKey() != null && !p.getApiKey().isBlank()) ? "****" : null;
        r.enabled = p.isEnabled();
        r.createdAt = p.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public LlmProviderType getType() { return type; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKeyMasked() { return apiKeyMasked; }
    public boolean isEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
