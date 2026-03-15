package com.example.contextengine.application.service;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.pipeline.PipelineContext;
import com.example.contextengine.domain.pipeline.StreamingContextPipeline;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.StreamingLlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import com.example.contextengine.domain.step.ContextBudgetManager;
import com.example.contextengine.domain.step.WebSearchRelevanceFilter;
import com.example.contextengine.infrastructure.config.ContextEngineProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multi-step Chain of Thought orchestrator for Research mode.
 *
 * Flow:
 *   Phase 0: Partial pipeline (vagueness-detection + query-rewriting)
 *   Phase 1: LLM generates a research plan (2-5 sub-questions)
 *   Phase 2: Web search per sub-question, with relevance filtering
 *   Phase 2.5: Deduplication by URL + context budget trimming
 *   Phase 3: Streaming LLM synthesis with all accumulated sources
 */
@Service
public class DeepResearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DeepResearchOrchestrator.class);

    private static final String PLANNING_PROMPT = """
            Tu es un expert en planification de recherche. Analyse la question et cree un plan.
            Identifie 2 a 5 sous-questions specifiques, optimisees pour la recherche web.
            Regles:
            - Specifiques et recherchables sur le web
            - Complementaires, couvrant differents aspects
            - Concises (< 100 caracteres chacune)
            - 2-3 pour questions simples, 4-5 pour complexes
            Reponds UNIQUEMENT en JSON valide: {"subQuestions": ["q1", "q2", ...]}
            """;

    private final LlmPort llmPort;
    private final WebSearchPort webSearchPort;
    private final StreamingLlmPort streamingLlmPort;
    private final StreamingContextPipeline streamingPipeline;
    private final ContextEngineProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeepResearchOrchestrator(LlmPort llmPort,
                                     WebSearchPort webSearchPort,
                                     StreamingLlmPort streamingLlmPort,
                                     StreamingContextPipeline streamingPipeline,
                                     ContextEngineProperties properties) {
        this.llmPort = llmPort;
        this.webSearchPort = webSearchPort;
        this.streamingLlmPort = streamingLlmPort;
        this.streamingPipeline = streamingPipeline;
        this.properties = properties;
    }

    /**
     * Main entry point: orchestrates multi-step deep research.
     */
    public void orchestrate(String prompt, String systemPrompt,
                             List<ConversationMessage> conversationHistory,
                             String documentContext, boolean webSearchRequested,
                             SseEmitter emitter) {

        // ── Phase 0: Partial pipeline (vagueness + query rewriting) ──
        log.info("[DEEP-RESEARCH] Phase 0: Running partial pipeline for prompt: {}", prompt);

        PipelineContext pipelineCtx = new PipelineContext(
                prompt, conversationHistory, documentContext,
                webSearchRequested, llmPort, webSearchPort,
                properties.getMaxContextTokens());

        ContextAnalysis partialAnalysis = streamingPipeline.runStreamingPartial(
                pipelineCtx, emitter,
                Set.of("vagueness-detection", "query-rewriting"));

        if (!partialAnalysis.isContinue()) {
            // Clarification needed — emit and stop
            emitClarificationEvent(emitter, partialAnalysis);
            emitter.complete();
            return;
        }

        String effectiveQuery = partialAnalysis.getRewrittenQuery() != null
                ? partialAnalysis.getRewrittenQuery() : prompt;

        // ── Phase 1: Generate research plan ──
        log.info("[DEEP-RESEARCH] Phase 1: Generating research plan for: {}", effectiveQuery);
        emitStepEvent(emitter, "planning", "running", "Elaboration du plan de recherche...", null);

        List<String> subQuestions = generateResearchPlan(effectiveQuery);

        emitStepEvent(emitter, "planning", "done", null,
                subQuestions.size() + " sous-questions identifiees");
        log.info("[DEEP-RESEARCH] Plan: {} sub-questions", subQuestions.size());

        // Emit research plan as thinking (Perplexity-style progressive thinking)
        StringBuilder researchThinking = new StringBuilder();
        String planThinking = buildPlanThinking(subQuestions);
        researchThinking.append(planThinking);
        emitThinking(emitter, planThinking);

        // ── Phase 2: Web search per sub-question ──
        log.info("[DEEP-RESEARCH] Phase 2: Executing {} sub-queries", subQuestions.size());
        List<SearchResult> allResults = new ArrayList<>();
        int citationOffset = 0;

        for (int i = 0; i < subQuestions.size(); i++) {
            String sq = subQuestions.get(i);
            String stepLabel = "[" + (i + 1) + "/" + subQuestions.size() + "] " + truncate(sq, 60);
            String stepId = "sq-" + (i + 1);

            emitStepEvent(emitter, stepId, "running", stepLabel, null);

            try {
                List<SearchResult> sqResults = executeSubQuery(sq, citationOffset);
                allResults.addAll(sqResults);
                citationOffset += sqResults.size();

                // Emit progressive sources
                if (!allResults.isEmpty()) {
                    emitSourcesEvent(emitter, allResults);
                }

                // Emit per-sub-query thinking (Perplexity-style findings)
                String sqThinking = buildSubQueryThinking(sq, i, subQuestions.size(), sqResults);
                researchThinking.append(sqThinking);
                emitThinking(emitter, sqThinking);

                emitStepEvent(emitter, stepId, "done", null, sqResults.size() + " source(s)");
                log.info("[DEEP-RESEARCH] Sub-query {}/{}: {} sources", i + 1, subQuestions.size(), sqResults.size());
            } catch (Exception e) {
                log.warn("[DEEP-RESEARCH] Sub-query {}/{} failed: {}", i + 1, subQuestions.size(), e.getMessage());
                String failThinking = buildSubQueryThinking(sq, i, subQuestions.size(), List.of());
                researchThinking.append(failThinking);
                emitThinking(emitter, failThinking);
                emitStepEvent(emitter, stepId, "done", null, "0 sources (erreur)");
            }
        }

        // ── Phase 2.5: Deduplication + context budget ──
        log.info("[DEEP-RESEARCH] Phase 2.5: Deduplication ({} raw results)", allResults.size());
        List<SearchResult> deduplicated = deduplicateByUrl(allResults);
        log.info("[DEEP-RESEARCH] After dedup: {} unique results", deduplicated.size());

        // Emit final deduplicated sources
        if (!deduplicated.isEmpty()) {
            emitSourcesEvent(emitter, deduplicated);
        }

        // Build web context and apply budget
        String webSearchContext = !deduplicated.isEmpty()
                ? webSearchPort.buildContextPrompt(deduplicated) : null;
        webSearchContext = applyContextBudget(prompt, conversationHistory, documentContext, webSearchContext);

        // ── Phase 3: Streaming synthesis ──
        log.info("[DEEP-RESEARCH] Phase 3: Streaming synthesis with {} sources", deduplicated.size());
        emitStepEvent(emitter, "synthesis", "running", "Synthese en cours...", null);

        // Emit synthesis header as thinking
        String synthesisHeader = "\n---\n## Synthese\n\n";
        researchThinking.append(synthesisHeader);
        emitThinking(emitter, synthesisHeader);

        String fullSystemPrompt = buildResearchSystemPrompt(systemPrompt, documentContext, webSearchContext);
        StringBuilder answerAccumulator = new StringBuilder();
        List<SearchResult> finalSources = deduplicated;

        streamingLlmPort.streamThinkAndAnswer(
                fullSystemPrompt, effectiveQuery, conversationHistory,
                thinking -> {
                    researchThinking.append(thinking.content());
                    sendEvent(emitter, "thinking", "{\"content\":" + quote(thinking.content()) + "}");
                },
                chunk -> {
                    answerAccumulator.append(chunk.content());
                    sendEvent(emitter, "answer", "{\"content\":" + quote(chunk.content()) + "}");
                },
                error -> {
                    log.error("[DEEP-RESEARCH] LLM synthesis error: {}", error.getMessage());
                    sendEvent(emitter, "error", "{\"message\":" + quote(error.getMessage()) + "}");
                    emitter.complete();
                },
                () -> {
                    emitStepEvent(emitter, "synthesis", "done", null, "Synthese terminee");

                    // Emit final sources again (for consistency)
                    if (!finalSources.isEmpty()) {
                        emitSourcesEvent(emitter, finalSources);
                    }

                    String donePayload = buildDonePayload(
                            answerAccumulator.toString(),
                            researchThinking.toString(),
                            finalSources, true);
                    sendEvent(emitter, "done", donePayload);
                    emitter.complete();
                    log.info("[DEEP-RESEARCH] Stream completed");
                }
        );
    }

    // ── Internal methods ────────────────────────────────────────────────────

    /**
     * Uses LLM to generate 2-5 sub-questions from the user query.
     * Falls back to a single query if JSON parsing fails.
     */
    private List<String> generateResearchPlan(String query) {
        try {
            String raw = llmPort.analyze(PLANNING_PROMPT, query);
            String json = extractJson(raw);
            JsonNode node = mapper.readTree(json);

            if (node.has("subQuestions") && node.get("subQuestions").isArray()) {
                List<String> subQuestions = new ArrayList<>();
                for (JsonNode sq : node.get("subQuestions")) {
                    String text = sq.asText().trim();
                    if (!text.isEmpty()) {
                        subQuestions.add(text);
                    }
                }
                if (!subQuestions.isEmpty()) {
                    return subQuestions;
                }
            }

            log.warn("[DEEP-RESEARCH] Plan JSON valid but no subQuestions, fallback to single query");
            return List.of(query);
        } catch (Exception e) {
            log.warn("[DEEP-RESEARCH] Failed to parse research plan: {}, fallback to single query", e.getMessage());
            return List.of(query);
        }
    }

    /**
     * Executes a single sub-query: web search + relevance filter + citation renumbering.
     */
    private List<SearchResult> executeSubQuery(String query, int citationOffset) {
        List<SearchResult> rawResults = webSearchPort.searchAndExtract(query);
        if (rawResults == null || rawResults.isEmpty()) {
            return List.of();
        }

        // Apply relevance filter
        Set<String> queryTerms = WebSearchRelevanceFilter.tokenize(query);
        List<SearchResult> filtered = rawResults.stream()
                .filter(r -> WebSearchRelevanceFilter.computeRelevanceScore(queryTerms, r) >= 0.15)
                .toList();

        // Keep originals if all were filtered out
        if (filtered.isEmpty()) {
            filtered = rawResults;
        }

        // Renumber citations with global offset
        List<SearchResult> renumbered = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            SearchResult r = filtered.get(i);
            renumbered.add(new SearchResult(
                    "[" + (citationOffset + i + 1) + "]",
                    r.sourceUrl(), r.sourceTitle(), r.extractedText()));
        }

        return renumbered;
    }

    /**
     * Deduplicates results by URL, keeping the first occurrence.
     * Re-numbers citations [1]..[N] after dedup.
     */
    private List<SearchResult> deduplicateByUrl(List<SearchResult> results) {
        LinkedHashMap<String, SearchResult> byUrl = new LinkedHashMap<>();
        for (SearchResult r : results) {
            byUrl.putIfAbsent(r.sourceUrl(), r);
        }

        List<SearchResult> deduplicated = new ArrayList<>();
        int idx = 1;
        for (SearchResult r : byUrl.values()) {
            deduplicated.add(new SearchResult(
                    "[" + idx + "]", r.sourceUrl(), r.sourceTitle(), r.extractedText()));
            idx++;
        }

        return deduplicated;
    }

    /**
     * Applies context budget: trims webSearchContext if combined tokens exceed max.
     */
    private String applyContextBudget(String prompt, List<ConversationMessage> history,
                                       String documentContext, String webSearchContext) {
        if (webSearchContext == null) return null;

        int maxTokens = properties.getMaxContextTokens();
        int promptTokens = ContextBudgetManager.estimateTokens(prompt);
        int historyTokens = history.stream()
                .mapToInt(m -> ContextBudgetManager.estimateTokens(m.content()))
                .sum();
        int docTokens = documentContext != null ? ContextBudgetManager.estimateTokens(documentContext) : 0;
        int webTokens = ContextBudgetManager.estimateTokens(webSearchContext);

        int total = promptTokens + historyTokens + docTokens + webTokens;

        if (total > maxTokens) {
            int allowedWeb = Math.max(0, webTokens - (total - maxTokens));
            if (allowedWeb < webTokens) {
                int maxChars = allowedWeb * 4;
                if (maxChars < webSearchContext.length()) {
                    webSearchContext = webSearchContext.substring(0, maxChars) + "\n[...tronque]";
                    log.info("[DEEP-RESEARCH] Trimmed webSearchContext: {} -> {} tokens",
                            webTokens, ContextBudgetManager.estimateTokens(webSearchContext));
                }
            }
        }

        return webSearchContext;
    }

    /**
     * Extracts JSON from raw LLM response (may be wrapped in text or markdown).
     */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    // ── Thinking content builders (Perplexity-style progressive analysis) ───

    private String buildPlanThinking(List<String> subQuestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Plan de recherche\n\n");
        sb.append("L'analyse sera menee en ").append(subQuestions.size()).append(" axes :\n\n");
        for (int i = 0; i < subQuestions.size(); i++) {
            sb.append(i + 1).append(". ").append(subQuestions.get(i)).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildSubQueryThinking(String query, int index, int total,
                                          List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n## [").append(index + 1).append("/").append(total).append("] ")
                .append(query).append("\n\n");
        if (results.isEmpty()) {
            sb.append("*Aucune source trouvee.*\n\n");
        } else {
            sb.append("**").append(results.size()).append(" source(s) trouvee(s) :**\n\n");
            for (SearchResult r : results) {
                sb.append("- ").append(r.citationId()).append(" **").append(r.sourceTitle()).append("**");
                if (r.extractedText() != null && !r.extractedText().isBlank()) {
                    String excerpt = truncate(r.extractedText().replaceAll("\\s+", " ").trim(), 150);
                    sb.append(" — ").append(excerpt);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void emitThinking(SseEmitter emitter, String content) {
        sendEvent(emitter, "thinking", "{\"content\":" + quote(content) + "}");
    }

    // ── SSE event helpers ────────────────────────────────────────────────────

    private void emitStepEvent(SseEmitter emitter, String stepId, String status,
                                String label, String detail) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"stepId\":\"").append(stepId).append("\",");
        json.append("\"status\":\"").append(status).append("\"");
        if (label != null) json.append(",\"label\":\"").append(escapeJson(label)).append("\"");
        if (detail != null) json.append(",\"detail\":\"").append(escapeJson(detail)).append("\"");
        json.append("}");
        sendEvent(emitter, "step", json.toString());
    }

    private void emitSourcesEvent(SseEmitter emitter, List<SearchResult> sources) {
        try {
            var node = mapper.createObjectNode();
            var sourcesArray = node.putArray("sources");
            for (SearchResult s : sources) {
                var sourceNode = sourcesArray.addObject();
                sourceNode.put("citationId", s.citationId());
                sourceNode.put("sourceUrl", s.sourceUrl());
                sourceNode.put("sourceTitle", s.sourceTitle());
            }
            sendEvent(emitter, "sources", mapper.writeValueAsString(node));
        } catch (Exception e) {
            sendEvent(emitter, "sources", "{\"sources\":[]}");
        }
    }

    private void emitClarificationEvent(SseEmitter emitter, ContextAnalysis analysis) {
        try {
            var node = mapper.createObjectNode();
            node.put("message", analysis.getClarificationMessage());
            var sugArray = node.putArray("suggestions");
            if (analysis.getSuggestions() != null) {
                analysis.getSuggestions().forEach(sugArray::add);
            }
            sendEvent(emitter, "clarification", mapper.writeValueAsString(node));
        } catch (Exception e) {
            sendEvent(emitter, "clarification",
                    "{\"message\":" + quote(analysis.getClarificationMessage()) + "}");
        }
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

    private String buildResearchSystemPrompt(String baseSystemPrompt, String documentContext,
                                              String webSearchContext) {
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
            sb.append("\n\n## Mode recherche web approfondie\n");
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

    private void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("[DEEP-RESEARCH] Failed to send event '{}': {}", eventName, e.getMessage());
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

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
