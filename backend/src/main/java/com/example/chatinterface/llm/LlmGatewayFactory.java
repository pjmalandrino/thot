package com.example.chatinterface.llm;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Crée et met en cache les instances LlmGateway par provider + modèle.
 * La clé de cache est "providerId:modelName".
 * Appeler invalidate() après toute modification de la config d'un provider.
 */
@Component
public class LlmGatewayFactory {

    private final ConcurrentHashMap<String, LlmGateway> cache = new ConcurrentHashMap<>();

    public LlmGateway getGateway(LlmProvider provider, String modelName) {
        String key = provider.getId() + ":" + modelName;
        return cache.computeIfAbsent(key, k -> create(provider, modelName));
    }

    public void invalidate(Long providerId) {
        cache.keySet().removeIf(key -> key.startsWith(providerId + ":"));
    }

    private LlmGateway create(LlmProvider provider, String modelName) {
        return switch (provider.getType()) {
            case OLLAMA  -> new OllamaGateway(provider.getBaseUrl(), modelName);
            case MISTRAL -> new MistralGateway(provider.getApiKey(), modelName);
        };
    }
}
