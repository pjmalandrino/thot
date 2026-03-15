package com.example.contextengine.domain.port.out;

import com.example.contextengine.domain.model.AnswerChunk;
import com.example.contextengine.domain.model.ThinkingChunk;

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
}
