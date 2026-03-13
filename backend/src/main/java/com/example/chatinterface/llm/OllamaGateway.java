package com.example.chatinterface.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ollama.OllamaChatModel;

import java.util.List;

/**
 * Gateway Ollama — instancié par LlmGatewayFactory.
 * Chaque combinaison (baseUrl, modelName) produit une instance distincte,
 * mise en cache dans la factory.
 */
public class OllamaGateway implements LlmGateway {

    private final OllamaChatModel model;

    public OllamaGateway(String baseUrl, String modelName) {
        this.model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    @Override
    public String generate(List<ChatMessage> messages) {
        return model.generate(messages).content().text();
    }
}
