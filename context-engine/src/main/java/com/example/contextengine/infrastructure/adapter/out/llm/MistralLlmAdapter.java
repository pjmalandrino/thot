package com.example.contextengine.infrastructure.adapter.out.llm;

import com.example.contextengine.domain.port.out.LlmPort;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.mistralai.MistralAiChatModel;

import java.util.List;

public class MistralLlmAdapter implements LlmPort {

    private final MistralAiChatModel model;

    public MistralLlmAdapter(String apiKey, String modelName) {
        this.model = MistralAiChatModel.builder()
                .apiKey(apiKey)
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
