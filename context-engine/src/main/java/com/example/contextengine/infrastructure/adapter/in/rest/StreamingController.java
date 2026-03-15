package com.example.contextengine.infrastructure.adapter.in.rest;

import com.example.contextengine.application.service.DeepResearchOrchestrator;
import com.example.contextengine.application.service.LabOrchestrator;
import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.port.out.StreamingLlmPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@RestController
public class StreamingController {

    private static final Logger log = LoggerFactory.getLogger(StreamingController.class);
    private static final long SSE_TIMEOUT = 600_000L;

    private final StreamingLlmPort streamingLlmPort;
    private final DeepResearchOrchestrator deepResearchOrchestrator;
    private final LabOrchestrator labOrchestrator;
    private final Executor streamingExecutor;
    private final ObjectMapper mapper = new ObjectMapper();

    public StreamingController(StreamingLlmPort streamingLlmPort,
                               DeepResearchOrchestrator deepResearchOrchestrator,
                               LabOrchestrator labOrchestrator,
                               @Qualifier("streamingExecutor") Executor streamingExecutor) {
        this.streamingLlmPort = streamingLlmPort;
        this.deepResearchOrchestrator = deepResearchOrchestrator;
        this.labOrchestrator = labOrchestrator;
        this.streamingExecutor = streamingExecutor;
    }

    /**
     * Think mode: direct Magistral streaming with thinking tokens.
     * No pipeline steps — just raw reasoning + answer.
     */
    @PostMapping(value = "/api/stream/think", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamThink(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.get("prompt");
        String documentContext = (String) body.get("documentContext");
        String baseSystemPrompt = (String) body.get("systemPrompt");
        List<ConversationMessage> conversationHistory = extractConversationHistory(body);
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
                            sendEvent(emitter, "thinking", "{\"content\":" + quote(chunk.content()) + "}");
                        },
                        chunk -> {
                            answerAccumulator.append(chunk.content());
                            sendEvent(emitter, "answer", "{\"content\":" + quote(chunk.content()) + "}");
                        },
                        error -> {
                            log.error("[STREAM-THINK] Error: {}", error.getMessage());
                            sendEvent(emitter, "error", "{\"message\":" + quote(error.getMessage()) + "}");
                            emitter.complete();
                        },
                        () -> {
                            String donePayload = buildDonePayload(
                                    answerAccumulator.toString(),
                                    thinkingAccumulator.toString(),
                                    List.of(), false);
                            sendEvent(emitter, "done", donePayload);
                            emitter.complete();
                            log.info("[STREAM-THINK] Stream completed");
                        }
                );
            } catch (Exception e) {
                log.error("[STREAM-THINK] Unexpected error: {}", e.getMessage(), e);
                sendEvent(emitter, "error", "{\"message\":" + quote(e.getMessage()) + "}");
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
    public SseEmitter streamResearch(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.get("prompt");
        String documentContext = (String) body.get("documentContext");
        String baseSystemPrompt = (String) body.get("systemPrompt");
        boolean webSearchRequested = Boolean.parseBoolean(String.valueOf(body.getOrDefault("webSearchRequested", "false")));
        List<ConversationMessage> conversationHistory = extractConversationHistory(body);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("[STREAM-RESEARCH] Starting research stream for prompt: {}", prompt);

        streamingExecutor.execute(() -> {
            try {
                deepResearchOrchestrator.orchestrate(
                        prompt, baseSystemPrompt, conversationHistory,
                        documentContext, webSearchRequested, emitter);
            } catch (Exception e) {
                log.error("[STREAM-RESEARCH] Unexpected error: {}", e.getMessage(), e);
                sendEvent(emitter, "error", "{\"message\":" + quote(e.getMessage()) + "}");
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * Lab mode: research + multi-section document writing.
     */
    @PostMapping(value = "/api/stream/lab", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLab(@RequestBody Map<String, Object> body) {
        String prompt = (String) body.get("prompt");
        String documentContext = (String) body.get("documentContext");
        String baseSystemPrompt = (String) body.get("systemPrompt");
        List<ConversationMessage> conversationHistory = extractConversationHistory(body);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("[STREAM-LAB] Starting lab stream for prompt: {}", prompt);

        streamingExecutor.execute(() -> {
            try {
                labOrchestrator.orchestrate(
                        prompt, baseSystemPrompt, conversationHistory,
                        documentContext, emitter);
            } catch (Exception e) {
                log.error("[STREAM-LAB] Unexpected error: {}", e.getMessage(), e);
                sendEvent(emitter, "error", "{\"message\":" + quote(e.getMessage()) + "}");
                emitter.complete();
            }
        });

        return emitter;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<ConversationMessage> extractConversationHistory(Map<String, Object> body) {
        Object historyObj = body.get("conversationHistory");
        if (historyObj == null) return List.of();
        if (historyObj instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> {
                        Map<String, Object> map = (Map<String, Object>) item;
                        return new ConversationMessage(
                                String.valueOf(map.get("role")),
                                String.valueOf(map.get("content")));
                    })
                    .toList();
        }
        return List.of();
    }

    private void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("[STREAM] Failed to send event '{}': {}", eventName, e.getMessage());
        }
    }

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

    private String buildDonePayload(String response, String thinking,
                                     List<SearchResult> sources, boolean autoWebSearchTriggered) {
        try {
            var node = mapper.createObjectNode();
            node.put("response", response);
            if (thinking != null && !thinking.isBlank()) node.put("thinking", thinking);
            node.put("autoWebSearchTriggered", autoWebSearchTriggered);
            var sourcesArray = node.putArray("sources");
            for (SearchResult s : sources) {
                var sourceNode = sourcesArray.addObject();
                sourceNode.put("citationId", s.citationId());
                sourceNode.put("sourceUrl", s.sourceUrl());
                sourceNode.put("sourceTitle", s.sourceTitle());
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"response\":" + quote(response) + ",\"sources\":[]}";
        }
    }

    private String quote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }
}
