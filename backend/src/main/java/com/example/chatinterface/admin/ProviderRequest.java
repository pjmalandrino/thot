package com.example.chatinterface.admin;

import com.example.chatinterface.llm.LlmProviderType;

public class ProviderRequest {

    private String name;
    private LlmProviderType type;
    private String baseUrl;
    private String apiKey;
    private Boolean enabled;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LlmProviderType getType() { return type; }
    public void setType(LlmProviderType type) { this.type = type; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
