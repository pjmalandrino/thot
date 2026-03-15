package com.example.contextengine.application.support;

import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import com.example.contextengine.domain.step.ContextBudgetManager;
import com.example.contextengine.domain.step.WebSearchRelevanceFilter;
import com.example.contextengine.infrastructure.config.ContextEngineProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Centralizes the research phase logic shared by DeepResearchOrchestrator and LabOrchestrator:
 * research plan generation, sub-query execution, deduplication, context budget, and thinking builders.
 */
@Component
public class ResearchPhaseExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResearchPhaseExecutor.class);

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
    private final ContextEngineProperties properties;
    private final SseEmitterHelper sseHelper;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResearchPhaseExecutor(LlmPort llmPort,
                                  WebSearchPort webSearchPort,
                                  ContextEngineProperties properties,
                                  SseEmitterHelper sseHelper) {
        this.llmPort = llmPort;
        this.webSearchPort = webSearchPort;
        this.properties = properties;
        this.sseHelper = sseHelper;
    }

    // ── Research plan generation ──────────────────────────────────────────────

    /**
     * Uses LLM to generate 2-5 sub-questions from the user query.
     * Falls back to a single query if JSON parsing fails.
     */
    public List<String> generateResearchPlan(String query) {
        try {
            String raw = llmPort.analyze(PLANNING_PROMPT, query);
            String json = sseHelper.extractJson(raw);
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

            log.warn("[RESEARCH] Plan JSON valid but no subQuestions, fallback to single query");
            return List.of(query);
        } catch (Exception e) {
            log.warn("[RESEARCH] Failed to parse research plan: {}, fallback to single query", e.getMessage());
            return List.of(query);
        }
    }

    // ── Sub-query execution ──────────────────────────────────────────────────

    /**
     * Executes a single sub-query: web search + relevance filter + citation renumbering.
     */
    public List<SearchResult> executeSubQuery(String query, int citationOffset) {
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

    // ── Deduplication ────────────────────────────────────────────────────────

    /**
     * Deduplicates results by URL, keeping the first occurrence.
     * Re-numbers citations [1]..[N] after dedup.
     */
    public List<SearchResult> deduplicateByUrl(List<SearchResult> results) {
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

    // ── Context budget ───────────────────────────────────────────────────────

    /**
     * Applies context budget: trims webSearchContext if combined tokens exceed max.
     */
    public String applyContextBudget(String prompt, List<ConversationMessage> history,
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
                    log.info("[RESEARCH] Trimmed webSearchContext: {} -> {} tokens",
                            webTokens, ContextBudgetManager.estimateTokens(webSearchContext));
                }
            }
        }

        return webSearchContext;
    }

    // ── Web context prompt ───────────────────────────────────────────────────

    /**
     * Builds the web search context prompt from search results.
     */
    public String buildWebContextPrompt(List<SearchResult> results) {
        return webSearchPort.buildContextPrompt(results);
    }

    // ── Thinking content builders ────────────────────────────────────────────

    public String buildPlanThinking(List<String> subQuestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Plan de recherche\n\n");
        sb.append("L'analyse sera menee en ").append(subQuestions.size()).append(" axes :\n\n");
        for (int i = 0; i < subQuestions.size(); i++) {
            sb.append(i + 1).append(". ").append(subQuestions.get(i)).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    public String buildSubQueryThinking(String query, int index, int total,
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
                    String excerpt = sseHelper.truncate(r.extractedText().replaceAll("\\s+", " ").trim(), 150);
                    sb.append(" — ").append(excerpt);
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
