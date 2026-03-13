package com.example.chatinterface.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.mistralai.MistralAiChatModel;

import java.util.List;

/**
 * Gateway Mistral AI — instancié par LlmGatewayFactory.
 * Chaque combinaison (apiKey, modelName) produit une instance distincte,
 * mise en cache dans la factory.
 */
public class MistralGateway implements LlmGateway {

    private final MistralAiChatModel model;

    public MistralGateway(String apiKey, String modelName) {
        this.model = MistralAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }

    @Override
    public String generate(List<ChatMessage> messages) {
        return model.generate(messages).content().text();
    }
}
