package com.example.contextengine.domain.port.out;

import com.example.contextengine.domain.model.AnswerChunk;
import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.ThinkingChunk;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port for streaming LLM calls with thinking token support (Magistral).
 */
public interface StreamingLlmPort {

    void streamThinkAndAnswer(String systemPrompt, String userMessage,
                              Consumer<ThinkingChunk> onThinking,
                              Consumer<AnswerChunk> onAnswer,
                              Consumer<Throwable> onError,
                              Runnable onComplete);

    /**
     * Streaming with conversation history injected as chat messages.
     * History messages are inserted between system prompt and user message.
     */
    default void streamThinkAndAnswer(String systemPrompt, String userMessage,
                                      List<ConversationMessage> conversationHistory,
                                      Consumer<ThinkingChunk> onThinking,
                                      Consumer<AnswerChunk> onAnswer,
                                      Consumer<Throwable> onError,
                                      Runnable onComplete) {
        // Default: ignore history for backward compatibility
        streamThinkAndAnswer(systemPrompt, userMessage, onThinking, onAnswer, onError, onComplete);
    }
}
