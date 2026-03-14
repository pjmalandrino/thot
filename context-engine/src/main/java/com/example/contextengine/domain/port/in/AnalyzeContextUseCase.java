package com.example.contextengine.domain.port.in;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.ConversationMessage;

import java.util.List;

public interface AnalyzeContextUseCase {

    ContextAnalysis analyze(String prompt,
                            List<ConversationMessage> conversationHistory,
                            String documentContext,
                            boolean webSearchRequested);
}
