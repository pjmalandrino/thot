package com.example.contextengine.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "context")
public class ContextEngineProperties {

    private String primaryModel = "mistral-small-latest";
    private double minConfidence = 0.75;
    private FallbackPolicy fallbackPolicy = FallbackPolicy.CONTINUE;
    private String llmProvider = "ollama";
    private int maxContextTokens = 8192;

    public enum FallbackPolicy {
        CONTINUE, BLOCK
    }

    public String getPrimaryModel() { return primaryModel; }
    public void setPrimaryModel(String primaryModel) { this.primaryModel = primaryModel; }

    public double getMinConfidence() { return minConfidence; }
    public void setMinConfidence(double minConfidence) { this.minConfidence = minConfidence; }

    public FallbackPolicy getFallbackPolicy() { return fallbackPolicy; }
    public void setFallbackPolicy(FallbackPolicy fallbackPolicy) { this.fallbackPolicy = fallbackPolicy; }

    public String getLlmProvider() { return llmProvider; }
    public void setLlmProvider(String llmProvider) { this.llmProvider = llmProvider; }

    public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }
}
