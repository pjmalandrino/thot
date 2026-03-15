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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lab mode orchestrator: multi-section document writing with cumulative context.
 *
 * Flow:
 *   Phase A: Research (adapted from DeepResearchOrchestrator)
 *     A0: Partial pipeline (vagueness-detection + query-rewriting)
 *     A1: LLM generates research plan (2-5 sub-questions)
 *     A2: Web search per sub-question, with relevance filtering
 *     A2.5: Deduplication by URL + context budget trimming
 *
 *   Phase B: Document structure planning
 *     LLM generates 3-6 sections with titles and descriptions
 *
 *   Phase C: Sequential section writing
 *     For each section: stream thinking + answer with cumulative context
 */
@Service
public class LabOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LabOrchestrator.class);

    // ── Prompts ──────────────────────────────────────────────────────────────────

    private static final String RESEARCH_PLANNING_PROMPT = """
            Tu es un expert en planification de recherche. Analyse la question et cree un plan.
            Identifie 2 a 5 sous-questions specifiques, optimisees pour la recherche web.
            Regles:
            - Specifiques et recherchables sur le web
            - Complementaires, couvrant differents aspects
            - Concises (< 100 caracteres chacune)
            - 2-3 pour questions simples, 4-5 pour complexes
            Reponds UNIQUEMENT en JSON valide: {"subQuestions": ["q1", "q2", ...]}
            """;

    private static final String DOCUMENT_STRUCTURE_PROMPT = """
            Tu es un expert en redaction structuree. A partir de la question de l'utilisateur
            et des resultats de recherche disponibles, propose un plan de document detaille.
            Le plan doit contenir 3 a 6 sections, chacune avec un titre et une description
            courte du contenu attendu.
            Regles:
            - Chaque section doit couvrir un aspect distinct du sujet
            - L'ordre doit etre logique (introduction, contenu principal, conclusion)
            - Les titres doivent etre clairs et descriptifs
            - 3-4 sections pour sujets simples, 5-6 pour sujets complexes
            Reponds UNIQUEMENT en JSON valide:
            {"sections": [{"title": "Titre", "description": "Ce que cette section couvre"}, ...]}
            """;

    private static final String SECTION_WRITING_PROMPT = """
            Tu rediges la section "%s" d'un document structure.

            ## Description de la section
            %s

            ## Contenu deja redige
            %s

            ## Instructions
            - Redige uniquement le contenu de cette section
            - Ne repete PAS les informations deja couvertes dans les sections precedentes
            - Base-toi sur les sources de recherche. Cite avec [1], [2], etc.
            - Sois detaille, precis et factuel
            - Commence directement par le contenu (le titre est ajoute automatiquement)
            """;

    private static final String SUMMARIZE_SECTIONS_PROMPT = """
            Resume de maniere tres concise les sections suivantes d'un document en cours
            de redaction. Garde les points cles et les informations factuelles.
            Objectif: environ 30 pourcent de la taille originale.
            """;

    // ── Dependencies ─────────────────────────────────────────────────────────────

    private final LlmPort llmPort;
    private final WebSearchPort webSearchPort;
    private final StreamingLlmPort streamingLlmPort;
    private final StreamingContextPipeline streamingPipeline;
    private final ContextEngineProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public LabOrchestrator(LlmPort llmPort,
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

    // ── Inner record for section planning ────────────────────────────────────────

    record SectionPlan(String title, String description) {}

    // ── Main entry point ─────────────────────────────────────────────────────────

    /**
     * Main entry point: orchestrates multi-section document writing.
     * Phase A (research) → Phase B (structure) → Phase C (sequential writing).
     */
    public void orchestrate(String prompt, String systemPrompt,
                             List<ConversationMessage> conversationHistory,
                             String documentContext,
                             SseEmitter emitter) {

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE A: Research (adapted from DeepResearchOrchestrator)
        // ═══════════════════════════════════════════════════════════════════════

        // ── Phase A0: Partial pipeline (vagueness + query rewriting) ──
        log.info("[LAB] Phase A0: Running partial pipeline for prompt: {}", prompt);

        PipelineContext pipelineCtx = new PipelineContext(
                prompt, conversationHistory, documentContext,
                true, llmPort, webSearchPort,
                properties.getMaxContextTokens());

        ContextAnalysis partialAnalysis = streamingPipeline.runStreamingPartial(
                pipelineCtx, emitter,
                Set.of("vagueness-detection", "query-rewriting"));

        if (!partialAnalysis.isContinue()) {
            emitClarificationEvent(emitter, partialAnalysis);
            emitter.complete();
            return;
        }

        String effectiveQuery = partialAnalysis.getRewrittenQuery() != null
                ? partialAnalysis.getRewrittenQuery() : prompt;

        // ── Phase A1: Generate research plan ──
        log.info("[LAB] Phase A1: Generating research plan for: {}", effectiveQuery);
        emitStepEvent(emitter, "planning", "running", "Elaboration du plan de recherche...", null);

        List<String> subQuestions = generateResearchPlan(effectiveQuery);

        emitStepEvent(emitter, "planning", "done", null,
                subQuestions.size() + " sous-questions identifiees");
        log.info("[LAB] Plan: {} sub-questions", subQuestions.size());

        // Emit research plan as thinking
        StringBuilder researchThinking = new StringBuilder();
        String planThinking = buildPlanThinking(subQuestions);
        researchThinking.append(planThinking);
        emitThinking(emitter, planThinking);

        // ── Phase A2: Web search per sub-question ──
        log.info("[LAB] Phase A2: Executing {} sub-queries", subQuestions.size());
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

                if (!allResults.isEmpty()) {
                    emitSourcesEvent(emitter, allResults);
                }

                String sqThinking = buildSubQueryThinking(sq, i, subQuestions.size(), sqResults);
                researchThinking.append(sqThinking);
                emitThinking(emitter, sqThinking);

                emitStepEvent(emitter, stepId, "done", null, sqResults.size() + " source(s)");
                log.info("[LAB] Sub-query {}/{}: {} sources", i + 1, subQuestions.size(), sqResults.size());
            } catch (Exception e) {
                log.warn("[LAB] Sub-query {}/{} failed: {}", i + 1, subQuestions.size(), e.getMessage());
                String failThinking = buildSubQueryThinking(sq, i, subQuestions.size(), List.of());
                researchThinking.append(failThinking);
                emitThinking(emitter, failThinking);
                emitStepEvent(emitter, stepId, "done", null, "0 sources (erreur)");
            }
        }

        // ── Phase A2.5: Deduplication + context budget ──
        log.info("[LAB] Phase A2.5: Deduplication ({} raw results)", allResults.size());
        List<SearchResult> deduplicated = deduplicateByUrl(allResults);
        log.info("[LAB] After dedup: {} unique results", deduplicated.size());

        if (!deduplicated.isEmpty()) {
            emitSourcesEvent(emitter, deduplicated);
        }

        String webSearchContext = !deduplicated.isEmpty()
                ? webSearchPort.buildContextPrompt(deduplicated) : null;
        webSearchContext = applyContextBudget(prompt, conversationHistory, documentContext, webSearchContext);

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE B: Document structure planning
        // ═══════════════════════════════════════════════════════════════════════
        log.info("[LAB] Phase B: Planning document structure");
        emitStepEvent(emitter, "doc-planning", "running", "Planification de la structure du document...", null);

        List<SectionPlan> sections = planDocumentStructure(effectiveQuery, webSearchContext);

        emitStepEvent(emitter, "doc-planning", "done", null,
                sections.size() + " sections planifiees");
        log.info("[LAB] Document plan: {} sections", sections.size());

        // Emit structure as thinking
        String structureThinking = buildStructureThinking(sections);
        researchThinking.append(structureThinking);
        emitThinking(emitter, structureThinking);

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE C: Sequential section writing
        // ═══════════════════════════════════════════════════════════════════════
        log.info("[LAB] Phase C: Writing {} sections sequentially", sections.size());

        StringBuilder fullDocument = new StringBuilder();
        StringBuilder allThinking = new StringBuilder(researchThinking);
        List<SearchResult> finalSources = deduplicated;
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        for (int i = 0; i < sections.size(); i++) {
            if (errorOccurred.get()) break;

            SectionPlan section = sections.get(i);
            String stepId = "section-" + (i + 1);

            emitStepEvent(emitter, stepId, "running",
                    "Redaction: " + section.title(), null);

            // Emit section header as answer content (markdown)
            String sectionHeader = "\n\n## " + section.title() + "\n\n";
            sendEvent(emitter, "answer", "{\"content\":" + quote(sectionHeader) + "}");
            fullDocument.append(sectionHeader);

            // Build context for this section
            String previousSectionsContext = buildPreviousSectionsContext(
                    fullDocument.toString(), properties.getMaxContextTokens());

            String sectionPrompt = String.format(SECTION_WRITING_PROMPT,
                    section.title(), section.description(), previousSectionsContext);

            String sectionSystemPrompt = buildSectionSystemPrompt(
                    systemPrompt, documentContext, webSearchContext);

            // Section thinking header
            String sectionThinkingHeader = "\n---\n## Section " + (i + 1) + ": " + section.title() + "\n\n";
            allThinking.append(sectionThinkingHeader);
            emitThinking(emitter, sectionThinkingHeader);

            // Stream this section's content
            final int sectionIndex = i;
            StringBuilder sectionAnswer = new StringBuilder();

            streamingLlmPort.streamThinkAndAnswer(
                    sectionSystemPrompt, sectionPrompt, List.of(),
                    // onThinking
                    chunk -> {
                        allThinking.append(chunk.content());
                        sendEvent(emitter, "thinking", "{\"content\":" + quote(chunk.content()) + "}");
                    },
                    // onContent
                    chunk -> {
                        sectionAnswer.append(chunk.content());
                        fullDocument.append(chunk.content());
                        sendEvent(emitter, "answer", "{\"content\":" + quote(chunk.content()) + "}");
                    },
                    // onError
                    error -> {
                        log.error("[LAB] Section {} error: {}", sectionIndex + 1, error.getMessage());
                        emitStepEvent(emitter, stepId, "done", null, "Erreur de redaction");
                        errorOccurred.set(true);
                    },
                    // onComplete
                    () -> {
                        // Per-section completion — do NOT complete emitter
                        log.info("[LAB] Section {}/{} completed ({} chars)",
                                sectionIndex + 1, sections.size(), sectionAnswer.length());
                    }
            );

            if (!errorOccurred.get()) {
                emitStepEvent(emitter, stepId, "done", null, "Section terminee");
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // FINAL: Emit done event and complete
        // ═══════════════════════════════════════════════════════════════════════

        if (!finalSources.isEmpty()) {
            emitSourcesEvent(emitter, finalSources);
        }

        String donePayload = buildDonePayload(
                fullDocument.toString(),
                allThinking.toString(),
                finalSources, true);
        sendEvent(emitter, "done", donePayload);
        emitter.complete();
        log.info("[LAB] Stream completed — full document: {} chars", fullDocument.length());
    }

    // ── Phase A helpers (research) ───────────────────────────────────────────────

    private List<String> generateResearchPlan(String query) {
        try {
            String raw = llmPort.analyze(RESEARCH_PLANNING_PROMPT, query);
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

            log.warn("[LAB] Plan JSON valid but no subQuestions, fallback to single query");
            return List.of(query);
        } catch (Exception e) {
            log.warn("[LAB] Failed to parse research plan: {}, fallback to single query", e.getMessage());
            return List.of(query);
        }
    }

    private List<SearchResult> executeSubQuery(String query, int citationOffset) {
        List<SearchResult> rawResults = webSearchPort.searchAndExtract(query);
        if (rawResults == null || rawResults.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = WebSearchRelevanceFilter.tokenize(query);
        List<SearchResult> filtered = rawResults.stream()
                .filter(r -> WebSearchRelevanceFilter.computeRelevanceScore(queryTerms, r) >= 0.15)
                .toList();

        if (filtered.isEmpty()) {
            filtered = rawResults;
        }

        List<SearchResult> renumbered = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            SearchResult r = filtered.get(i);
            renumbered.add(new SearchResult(
                    "[" + (citationOffset + i + 1) + "]",
                    r.sourceUrl(), r.sourceTitle(), r.extractedText()));
        }

        return renumbered;
    }

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
                    log.info("[LAB] Trimmed webSearchContext: {} -> {} tokens",
                            webTokens, ContextBudgetManager.estimateTokens(webSearchContext));
                }
            }
        }

        return webSearchContext;
    }

    // ── Phase B helpers (document structure) ──────────────────────────────────────

    private List<SectionPlan> planDocumentStructure(String query, String webSearchContext) {
        try {
            String context = "Question: " + query;
            if (webSearchContext != null) {
                context += "\n\nSources disponibles (resume):\n" + truncate(webSearchContext, 2000);
            }

            String raw = llmPort.analyze(DOCUMENT_STRUCTURE_PROMPT, context);
            return parseSectionPlan(raw, query);
        } catch (Exception e) {
            log.warn("[LAB] Failed to plan document structure: {}, fallback to single section", e.getMessage());
            return List.of(new SectionPlan("Analyse complete", "Analyse detaillee du sujet: " + query));
        }
    }

    private List<SectionPlan> parseSectionPlan(String raw, String fallbackQuery) {
        try {
            String json = extractJson(raw);
            JsonNode node = mapper.readTree(json);

            if (node.has("sections") && node.get("sections").isArray()) {
                List<SectionPlan> sections = new ArrayList<>();
                for (JsonNode s : node.get("sections")) {
                    String title = s.has("title") ? s.get("title").asText().trim() : null;
                    String description = s.has("description") ? s.get("description").asText().trim() : "";
                    if (title != null && !title.isEmpty()) {
                        sections.add(new SectionPlan(title, description));
                    }
                }
                if (!sections.isEmpty()) {
                    // Cap at 6 sections
                    return sections.size() > 6 ? sections.subList(0, 6) : sections;
                }
            }

            log.warn("[LAB] Section plan JSON valid but no sections, fallback");
            return List.of(new SectionPlan("Analyse complete", "Analyse detaillee du sujet: " + fallbackQuery));
        } catch (Exception e) {
            log.warn("[LAB] Failed to parse section plan: {}, fallback", e.getMessage());
            return List.of(new SectionPlan("Analyse complete", "Analyse detaillee du sujet: " + fallbackQuery));
        }
    }

    // ── Phase C helpers (section writing) ────────────────────────────────────────

    /**
     * Builds the context of previously written sections.
     * If the text exceeds 1/3 of max tokens, summarizes it to fit.
     */
    private String buildPreviousSectionsContext(String previousText, int maxContextTokens) {
        if (previousText == null || previousText.isBlank()) {
            return "(Premiere section — pas de contenu precedent)";
        }

        int tokens = ContextBudgetManager.estimateTokens(previousText);
        int budget = maxContextTokens / 3;

        if (tokens <= budget) {
            return previousText;
        }

        // Summarize to fit budget
        log.info("[LAB] Previous sections too large ({} tokens, budget {}), summarizing", tokens, budget);
        try {
            return llmPort.analyze(SUMMARIZE_SECTIONS_PROMPT, previousText);
        } catch (Exception e) {
            log.warn("[LAB] Failed to summarize previous sections, truncating: {}", e.getMessage());
            int maxChars = budget * 4;
            return previousText.substring(0, Math.min(maxChars, previousText.length())) + "\n[...tronque]";
        }
    }

    private String buildSectionSystemPrompt(String baseSystemPrompt, String documentContext,
                                             String webSearchContext) {
        StringBuilder sb = new StringBuilder();
        if (baseSystemPrompt != null && !baseSystemPrompt.isBlank()) {
            sb.append(baseSystemPrompt);
        } else {
            sb.append("Tu es un assistant expert en redaction de documents detailles.");
        }

        if (documentContext != null && !documentContext.isBlank()) {
            sb.append("\n\n## Documents attaches\n");
            sb.append("Utilise leur contenu pour enrichir ta redaction.\n\n");
            sb.append(documentContext);
        }

        if (webSearchContext != null && !webSearchContext.isBlank()) {
            sb.append("\n\n## Sources de recherche\n");
            sb.append("Base-toi sur les sources fournies ci-dessous pour rediger.\n\n");
            sb.append("Regles strictes :\n");
            sb.append("- Cite tes sources avec [1], [2], etc. correspondant EXACTEMENT aux numeros ci-dessous.\n");
            sb.append("- N'invente AUCUN numero de source qui n'existe pas dans la liste.\n");
            sb.append("- Si les sources ne couvrent pas un point, dis-le clairement.\n\n");
            sb.append("Sources :\n").append(webSearchContext);
        }

        return sb.toString();
    }

    // ── Thinking content builders ────────────────────────────────────────────────

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

    private String buildStructureThinking(List<SectionPlan> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n---\n## Structure du document\n\n");
        sb.append("Le document sera structure en ").append(sections.size()).append(" sections :\n\n");
        for (int i = 0; i < sections.size(); i++) {
            sb.append(i + 1).append(". **").append(sections.get(i).title()).append("**");
            if (sections.get(i).description() != null && !sections.get(i).description().isEmpty()) {
                sb.append(" — ").append(sections.get(i).description());
            }
            sb.append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private void emitThinking(SseEmitter emitter, String content) {
        sendEvent(emitter, "thinking", "{\"content\":" + quote(content) + "}");
    }

    // ── SSE event helpers ────────────────────────────────────────────────────────

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

    private void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("[LAB] Failed to send event '{}': {}", eventName, e.getMessage());
        }
    }

    // ── String utilities ─────────────────────────────────────────────────────────

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
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
