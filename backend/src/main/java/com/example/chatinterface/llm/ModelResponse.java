package com.example.chatinterface.llm;

public class ModelResponse {

    private Long id;
    private String displayLabel;   // "ollama/llama3.2", "mistral/mistral-small"
    private String providerName;
    private String providerType;   // "OLLAMA" ou "MISTRAL"
    private String modelName;      // nom technique : "llama3.2:3b"
    private boolean enabled;

    public static ModelResponse from(LlmModel model) {
        ModelResponse r = new ModelResponse();
        r.id = model.getId();
        r.providerName = model.getProvider().getName();
        r.providerType = model.getProvider().getType().name();
        r.modelName = model.getModelName();
        r.displayLabel = model.getProvider().getName().toLowerCase() + "/" + model.getDisplayName();
        r.enabled = model.isEnabled();
        return r;
    }

    public Long getId() { return id; }
    public String getDisplayLabel() { return displayLabel; }
    public String getProviderName() { return providerName; }
    public String getProviderType() { return providerType; }
    public String getModelName() { return modelName; }
    public boolean isEnabled() { return enabled; }
}
