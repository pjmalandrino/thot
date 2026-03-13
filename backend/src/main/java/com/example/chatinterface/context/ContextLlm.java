package com.example.chatinterface.context;

/**
 * Port LLM interne au module context.
 * Decoupled de LangChain4j : les steps ne connaissent que cette interface.
 * L'adaptation LlmGateway -> ContextLlm se fait via un lambda dans le controller.
 */
@FunctionalInterface
public interface ContextLlm {
    String analyze(String systemPrompt, String userMessage);
}
