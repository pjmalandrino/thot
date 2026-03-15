package com.example.contextengine.infrastructure.config;

import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.StreamingLlmPort;
import com.example.contextengine.infrastructure.adapter.out.llm.MistralLlmAdapter;
import com.example.contextengine.infrastructure.adapter.out.llm.MistralStreamingLlmAdapter;
import com.example.contextengine.infrastructure.adapter.out.llm.OllamaLlmAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Bean
    public LlmPort llmPort(ContextEngineProperties properties,
                           @Value("${ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
                           @Value("${mistral.api-key:}") String mistralApiKey) {
        return switch (properties.getLlmProvider()) {
            case "ollama" -> new OllamaLlmAdapter(ollamaBaseUrl, properties.getPrimaryModel());
            case "mistral" -> new MistralLlmAdapter(mistralApiKey, properties.getPrimaryModel());
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + properties.getLlmProvider());
        };
    }

    @Bean
    public StreamingLlmPort streamingLlmPort(@Value("${mistral.api-key:}") String mistralApiKey) {
        return new MistralStreamingLlmAdapter(mistralApiKey, "magistral-small-latest");
    }
}
