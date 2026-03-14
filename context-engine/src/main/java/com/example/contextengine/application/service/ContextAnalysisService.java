package com.example.contextengine.application.service;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.pipeline.ContextPipeline;
import com.example.contextengine.domain.pipeline.PipelineContext;
import com.example.contextengine.domain.port.in.AnalyzeContextUseCase;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContextAnalysisService implements AnalyzeContextUseCase {

    private static final Logger log = LoggerFactory.getLogger(ContextAnalysisService.class);

    private final ContextPipeline pipeline;
    private final LlmPort llmPort;
    private final WebSearchPort webSearchPort;

    public ContextAnalysisService(ContextPipeline pipeline,
                                  LlmPort llmPort,
                                  WebSearchPort webSearchPort) {
        this.pipeline = pipeline;
        this.llmPort = llmPort;
        this.webSearchPort = webSearchPort;
    }

    @Override
    public ContextAnalysis analyze(String prompt,
                                   List<ConversationMessage> conversationHistory,
                                   String documentContext,
                                   boolean webSearchRequested) {
        log.info("[CONTEXT-ENGINE] Analyze request: prompt='{}', webSearch={}", prompt, webSearchRequested);

        PipelineContext context = new PipelineContext(
                prompt, conversationHistory, documentContext,
                webSearchRequested, llmPort, webSearchPort);

        ContextAnalysis result = pipeline.run(context);

        log.info("[CONTEXT-ENGINE] Result: status={}", result.getStatus());
        return result;
    }
}
