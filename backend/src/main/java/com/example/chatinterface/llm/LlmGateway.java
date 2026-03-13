package com.example.chatinterface.llm;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * Abstraction de l'appel LLM.
 * Switcher de provider = nouvelle implémentation, rien d'autre ne change.
 */
public interface LlmGateway {
    String generate(List<ChatMessage> messages);
}
