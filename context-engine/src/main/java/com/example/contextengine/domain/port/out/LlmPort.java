package com.example.contextengine.domain.port.out;

@FunctionalInterface
public interface LlmPort {
    String analyze(String systemPrompt, String userMessage);
}
