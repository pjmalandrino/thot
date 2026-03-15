package com.example.contextengine.infrastructure.adapter.in.rest;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.pipeline.PipelineContext;
import com.example.contextengine.domain.pipeline.StreamingContextPipeline;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.StreamingLlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import com.example.contextengine.infrastructure.config.ContextEngineProperties;
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
    private static final long SSE_TIMEOUT = 120_000L;

    private final StreamingLlmPort streamingLlmPort;
    private final LlmPort llmPort;
    private final WebSearchPort webSearchPort;
    private final StreamingContextPipeline streamingPipeline;
    private final ContextEngineProperties properties;
    private final Executor streamingExecutor;
    private final ObjectMapper mapper = new ObjectMapper();

    public StreamingController(StreamingLlmPort streamingLlmPort,
                               LlmPort llmPort,
                               WebSearchPort webSearchPort,
                               StreamingContextPipeline streamingPipeline,
                               ContextEngineProperties properties,
                               @Qualifier("streamingExecutor") Executor streamingExecutor) {
        this.streamingLlmPort = streamingLlmPort;
        this.llmPort = llmPort;
        this.webSearchPort = webSearchPort;
        this.streamingPipeline = streamingPipeline;
        this.properties = properties;
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
                // 1. Run streaming pipeline (emits step events) — with real conversation history
                PipelineContext context = new PipelineContext(
                        prompt, conversationHistory, documentContext,
                        webSearchRequested, llmPort, webSearchPort,
                        properties.getMaxContextTokens());

                ContextAnalysis analysis = streamingPipeline.runStreaming(context, emitter);

                // 2. If clarification needed, emit and stop
                if (!analysis.isContinue()) {
                    String clarificationPayload = buildClarificationPayload(
                            analysis.getClarificationMessage(), analysis.getSuggestions());
                    sendEvent(emitter, "clarification", clarificationPayload);
                    emitter.complete();
                    return;
                }

                // 3. Emit sources if web search produced results
                if (analysis.getWebSearchResults() != null && !analysis.getWebSearchResults().isEmpty()) {
                    String sourcesPayload = buildSourcesPayload(analysis.getWebSearchResults());
                    sendEvent(emitter, "sources", sourcesPayload);
                }

                // 4. Stream LLM synthesis
                String effectivePrompt = analysis.getRewrittenQuery() != null
                        ? analysis.getRewrittenQuery() : prompt;
                String systemPrompt = buildResearchSystemPrompt(baseSystemPrompt, documentContext, analysis.getWebSearchContext());

                sendEvent(emitter, "step",
                        "{\"stepId\":\"synthesis\",\"status\":\"running\",\"label\":\"Synthese en cours...\"}");

                StringBuilder answerAccumulator = new StringBuilder();
                StringBuilder thinkingAccumulator = new StringBuilder();

                streamingLlmPort.streamThinkAndAnswer(
                        systemPrompt, effectivePrompt, conversationHistory,
                        thinking -> {
                            thinkingAccumulator.append(thinking.content());
                            sendEvent(emitter, "thinking", "{\"content\":" + quote(thinking.content()) + "}");
                        },
                        chunk -> {
                            answerAccumulator.append(chunk.content());
                            sendEvent(emitter, "answer", "{\"content\":" + quote(chunk.content()) + "}");
                        },
                        error -> {
                            log.error("[STREAM-RESEARCH] LLM error: {}", error.getMessage());
                            sendEvent(emitter, "error", "{\"message\":" + quote(error.getMessage()) + "}");
                            emitter.complete();
                        },
                        () -> {
                            sendEvent(emitter, "step",
                                    "{\"stepId\":\"synthesis\",\"status\":\"done\",\"detail\":\"Synthese terminee\"}");

                            String donePayload = buildDonePayload(
                                    answerAccumulator.toString(),
                                    thinkingAccumulator.toString(),
                                    analysis.getWebSearchResults() != null ? analysis.getWebSearchResults() : List.of(),
                                    analysis.isAutoWebSearchTriggered());
                            sendEvent(emitter, "done", donePayload);
                            emitter.complete();
                            log.info("[STREAM-RESEARCH] Stream completed");
                        }
                );
            } catch (Exception e) {
                log.error("[STREAM-RESEARCH] Unexpected error: {}", e.getMessage(), e);
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

    private String buildResearchSystemPrompt(String baseSystemPrompt, String documentContext, String webSearchContext) {
        StringBuilder sb = new StringBuilder();
        if (baseSystemPrompt != null && !baseSystemPrompt.isBlank()) {
            sb.append(baseSystemPrompt);
        } else {
            sb.append("Tu es un assistant de recherche.");
        }
        if (documentContext != null && !documentContext.isBlank()) {
            sb.append("\n\n## Documents attaches\n");
            sb.append("Utilise leur contenu pour repondre aux questions.\n\n");
            sb.append(documentContext);
        }
        if (webSearchContext != null && !webSearchContext.isBlank()) {
            sb.append("\n\n## Mode recherche web\n");
            sb.append("Reponds en te basant UNIQUEMENT sur les sources fournies ci-dessous.\n\n");
            sb.append("Regles strictes :\n");
            sb.append("- Cite tes sources avec [1], [2], etc. correspondant EXACTEMENT aux numeros ci-dessous.\n");
            sb.append("- N'invente AUCUN numero de source qui n'existe pas dans la liste.\n");
            sb.append("- Si les sources ne couvrent pas un point, dis-le clairement.\n");
            sb.append("- A la fin, ajoute une section 'Sources :' listant les URLs citees.\n\n");
            sb.append("Sources :\n").append(webSearchContext);
        } else {
            sb.append("\n\nSynthetise les informations trouvees et fournis une reponse precise.");
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

    private String buildSourcesPayload(List<SearchResult> sources) {
        try {
            var node = mapper.createObjectNode();
            var sourcesArray = node.putArray("sources");
            for (SearchResult s : sources) {
                var sourceNode = sourcesArray.addObject();
                sourceNode.put("citationId", s.citationId());
                sourceNode.put("sourceUrl", s.sourceUrl());
                sourceNode.put("sourceTitle", s.sourceTitle());
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"sources\":[]}";
        }
    }

    private String buildClarificationPayload(String message, List<String> suggestions) {
        try {
            var node = mapper.createObjectNode();
            node.put("message", message);
            var sugArray = node.putArray("suggestions");
            if (suggestions != null) suggestions.forEach(sugArray::add);
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"message\":" + quote(message) + "}";
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
