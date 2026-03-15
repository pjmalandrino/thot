package com.example.contextengine.infrastructure.adapter.in.rest;

import com.example.contextengine.application.service.DeepResearchOrchestrator;
import com.example.contextengine.application.service.LabOrchestrator;
import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.application.support.SseEmitterHelper;
import com.example.contextengine.domain.port.out.StreamingLlmPort;
import com.example.contextengine.infrastructure.adapter.in.rest.dto.StreamingRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

@RestController
public class StreamingController {

    private static final Logger log = LoggerFactory.getLogger(StreamingController.class);
    private static final long SSE_TIMEOUT = 600_000L;

    private final StreamingLlmPort streamingLlmPort;
    private final DeepResearchOrchestrator deepResearchOrchestrator;
    private final LabOrchestrator labOrchestrator;
    private final Executor streamingExecutor;
    private final SseEmitterHelper sseHelper;

    public StreamingController(StreamingLlmPort streamingLlmPort,
                               DeepResearchOrchestrator deepResearchOrchestrator,
                               LabOrchestrator labOrchestrator,
                               @Qualifier("streamingExecutor") Executor streamingExecutor,
                               SseEmitterHelper sseHelper) {
        this.streamingLlmPort = streamingLlmPort;
        this.deepResearchOrchestrator = deepResearchOrchestrator;
        this.labOrchestrator = labOrchestrator;
        this.streamingExecutor = streamingExecutor;
        this.sseHelper = sseHelper;
    }

    /**
     * Think mode: direct Magistral streaming with thinking tokens.
     * No pipeline steps — just raw reasoning + answer.
     */
    @PostMapping(value = "/api/stream/think", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamThink(@RequestBody StreamingRequestDto request) {
        String prompt = request.prompt();
        String documentContext = request.documentContext();
        String baseSystemPrompt = request.systemPrompt();
        List<ConversationMessage> conversationHistory = request.toDomainHistory();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("[STREAM-THINK] Starting think stream for prompt: {}", prompt);

        streamingExecutor.execute(() -> {
            StringBuilder thinkingAccumulator = new StringBuilder();
            StringBuilder answerAccumulator = new StringBuilder();

            try {
                String systemPrompt = buildThinkSystemPrompt(baseSystemPrompt, documentContext);

                streamingLlmPort.streamThinkAndAnswer(
                        systemPrompt, prompt, conversationHistory,
                        chunk -> {
                            thinkingAccumulator.append(chunk.content());
                            sseHelper.sendEvent(emitter, "thinking", "{\"content\":" + sseHelper.quote(chunk.content()) + "}");
                        },
                        chunk -> {
                            answerAccumulator.append(chunk.content());
                            sseHelper.sendEvent(emitter, "answer", "{\"content\":" + sseHelper.quote(chunk.content()) + "}");
                        },
                        error -> {
                            log.error("[STREAM-THINK] Error: {}", error.getMessage());
                            sseHelper.sendEvent(emitter, "error", "{\"message\":" + sseHelper.quote(error.getMessage()) + "}");
                            emitter.complete();
                        },
                        () -> {
                            String donePayload = sseHelper.buildDonePayload(
                                    answerAccumulator.toString(),
                                    thinkingAccumulator.toString(),
                                    List.of(), false);
                            sseHelper.sendEvent(emitter, "done", donePayload);
                            emitter.complete();
                            log.info("[STREAM-THINK] Stream completed");
                        }
                );
            } catch (Exception e) {
                log.error("[STREAM-THINK] Unexpected error: {}", e.getMessage(), e);
                sseHelper.sendEvent(emitter, "error", "{\"message\":" + sseHelper.quote(e.getMessage()) + "}");
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * Research mode: D-CoT orchestrated pipeline with step events + streaming synthesis.
     * Runs the full context pipeline, emitting step events, then streams the LLM synthesis.
     */
    @PostMapping(value = "/api/stream/research", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamResearch(@RequestBody StreamingRequestDto request) {
        String prompt = request.prompt();
        String documentContext = request.documentContext();
        String baseSystemPrompt = request.systemPrompt();
        boolean webSearchRequested = request.isWebSearchRequestedOrDefault();
        List<ConversationMessage> conversationHistory = request.toDomainHistory();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("[STREAM-RESEARCH] Starting research stream for prompt: {}", prompt);

        streamingExecutor.execute(() -> {
            try {
                deepResearchOrchestrator.orchestrate(
                        prompt, baseSystemPrompt, conversationHistory,
                        documentContext, webSearchRequested, emitter);
            } catch (Exception e) {
                log.error("[STREAM-RESEARCH] Unexpected error: {}", e.getMessage(), e);
                sseHelper.sendEvent(emitter, "error", "{\"message\":" + sseHelper.quote(e.getMessage()) + "}");
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * Lab mode: research + multi-section document writing.
     */
    @PostMapping(value = "/api/stream/lab", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLab(@RequestBody StreamingRequestDto request) {
        String prompt = request.prompt();
        String documentContext = request.documentContext();
        String baseSystemPrompt = request.systemPrompt();
        List<ConversationMessage> conversationHistory = request.toDomainHistory();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("[STREAM-LAB] Starting lab stream for prompt: {}", prompt);

        streamingExecutor.execute(() -> {
            try {
                labOrchestrator.orchestrate(
                        prompt, baseSystemPrompt, conversationHistory,
                        documentContext, emitter);
            } catch (Exception e) {
                log.error("[STREAM-LAB] Unexpected error: {}", e.getMessage(), e);
                sseHelper.sendEvent(emitter, "error", "{\"message\":" + sseHelper.quote(e.getMessage()) + "}");
                emitter.complete();
            }
        });

        return emitter;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String buildThinkSystemPrompt(String baseSystemPrompt, String documentContext) {
        StringBuilder sb = new StringBuilder();
        if (baseSystemPrompt != null && !baseSystemPrompt.isBlank()) {
            sb.append(baseSystemPrompt);
        } else {
            sb.append("Tu es un assistant expert en raisonnement.");
        }
        sb.append("\n\nAnalyse le probleme en profondeur, etape par etape. ");
        sb.append("Fournis une reponse claire et structuree.");
        if (documentContext != null && !documentContext.isBlank()) {
            sb.append("\n\nContexte documentaire:\n").append(documentContext);
        }
        return sb.toString();
    }

}
