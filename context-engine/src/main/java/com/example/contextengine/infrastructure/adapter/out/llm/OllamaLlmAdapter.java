package com.example.contextengine.infrastructure.adapter.out.llm;

import com.example.contextengine.domain.port.out.LlmPort;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.util.List;

public class OllamaLlmAdapter implements LlmPort {

    private final OllamaChatModel model;

    public OllamaLlmAdapter(String baseUrl, String modelName) {
        this.model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    @Override
    public String analyze(String systemPrompt, String userMessage) {
        return model.generate(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMessage)
        )).content().text();
    }
}
