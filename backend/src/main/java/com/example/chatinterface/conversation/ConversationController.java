package com.example.chatinterface.conversation;

import com.example.chatinterface.contextengine.StreamingContextEngineClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private static final long SSE_TIMEOUT = 600_000L;

    private final ConversationService conversationService;
    private final StreamingContextEngineClient streamingClient;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();
    private final ObjectMapper mapper = new ObjectMapper();

    public ConversationController(ConversationService conversationService,
                                   StreamingContextEngineClient streamingClient) {
        this.conversationService = conversationService;
        this.streamingClient = streamingClient;
    }

    @GetMapping
    public List<Conversation> getAll(@RequestParam(required = false) Long thotspaceId) {
        return conversationService.getConversations(thotspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation create(@RequestBody CreateConversationRequest request) {
        return conversationService.createConversation(request.getThotspaceId());
    }

    @GetMapping("/{id}/completions")
    public List<CompletionResponse> getCompletions(@PathVariable Long id) {
        return conversationService.getCompletions(id).stream()
                .map(CompletionResponse::from)
                .toList();
    }

    @PostMapping("/{id}/completions")
    @ResponseStatus(HttpStatus.CREATED)
    public CompletionResponse complete(@PathVariable Long id, @RequestBody CompletionRequest request) {
        return conversationService.complete(id, request.getPrompt(), request.getModelId(), request.getClarificationContext());
    }

    /**
     * SSE streaming endpoint for Think and Research modes.
     * Proxies SSE from context-engine, then persists on completion.
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCompletion(@PathVariable Long id,
                                        @RequestParam String prompt,
                                        @RequestParam Long modelId,
                                        @RequestParam String mode) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        log.info("[STREAM] Starting {} stream for conversation {} prompt: {}", mode, id, prompt);

        streamExecutor.execute(() -> {
            try {
                String documentContext = conversationService.getDocumentContext(id);
                String baseSystemPrompt = conversationService.getBaseSystemPrompt(id);
                var history = conversationService.buildRecentHistory(id);
                String endpoint = switch (mode) {
                    case "think" -> "/api/stream/think";
                    case "lab" -> "/api/stream/lab";
                    default -> "/api/stream/research";
                };

                Map<String, Object> params = new LinkedHashMap<>();
                params.put("prompt", prompt);
                if (documentContext != null && !documentContext.isBlank()) {
                    params.put("documentContext", documentContext);
                }
                params.put("systemPrompt", baseSystemPrompt);
                params.put("conversationHistory", history);
                if ("research".equals(mode)) {
                    params.put("webSearchRequested", true);
                }

                streamingClient.proxyStream(endpoint, params, emitter,
                        doneData -> {
                            // Parse done event and persist
                            try {
                                JsonNode doneNode = mapper.readTree(doneData);
                                String response = doneNode.has("response") ? doneNode.get("response").asText() : "";
                                String thinking = doneNode.has("thinking") ? doneNode.get("thinking").asText() : null;
                                boolean autoWebSearch = doneNode.has("autoWebSearchTriggered")
                                        && doneNode.get("autoWebSearchTriggered").asBoolean(false);

                                List<SourceInfo> sources = new ArrayList<>();
                                if (doneNode.has("sources")) {
                                    for (JsonNode s : doneNode.get("sources")) {
                                        sources.add(new SourceInfo(
                                                s.has("citationId") ? s.get("citationId").asText() : "",
                                                s.has("sourceUrl") ? s.get("sourceUrl").asText() : "",
                                                s.has("sourceTitle") ? s.get("sourceTitle").asText() : "",
                                                null));
                                    }
                                }

                                conversationService.persistStreamResult(id, prompt, mode,
                                        response, thinking, sources, autoWebSearch);
                                log.info("[STREAM] Persisted {} interaction for conversation {}", mode, id);
                            } catch (Exception e) {
                                log.error("[STREAM] Failed to persist: {}", e.getMessage());
                            }
                            emitter.complete();
                        },
                        error -> {
                            log.error("[STREAM] Error: {}", error.getMessage());
                            emitter.completeWithError(error);
                        });
            } catch (Exception e) {
                log.error("[STREAM] Unexpected error: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PatchMapping("/{id}")
    public Conversation rename(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return conversationService.renameConversation(id, body.get("title"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
    }
}
