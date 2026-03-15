package com.example.contextengine.application.service;

import com.example.contextengine.application.support.ResearchPhaseExecutor;
import com.example.contextengine.application.support.SseEmitterHelper;
import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.pipeline.PipelineContext;
import com.example.contextengine.domain.pipeline.StreamingContextPipeline;
import com.example.contextengine.domain.port.out.LlmPort;
import com.example.contextengine.domain.port.out.StreamingLlmPort;
import com.example.contextengine.domain.port.out.WebSearchPort;
import com.example.contextengine.domain.step.ContextBudgetManager;
import com.example.contextengine.infrastructure.config.ContextEngineProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lab mode orchestrator: multi-section document writing with cumulative context.
 *
 * Flow:
 *   Phase A: Research (delegated to ResearchPhaseExecutor)
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

    // ── Prompts (unique to Lab) ──────────────────────────────────────────────────

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
    private final ResearchPhaseExecutor researchExecutor;
    private final SseEmitterHelper sseHelper;
    private final ObjectMapper mapper = new ObjectMapper();

    public LabOrchestrator(LlmPort llmPort,
                           WebSearchPort webSearchPort,
                           StreamingLlmPort streamingLlmPort,
                           StreamingContextPipeline streamingPipeline,
                           ContextEngineProperties properties,
                           ResearchPhaseExecutor researchExecutor,
                           SseEmitterHelper sseHelper) {
        this.llmPort = llmPort;
        this.webSearchPort = webSearchPort;
        this.streamingLlmPort = streamingLlmPort;
        this.streamingPipeline = streamingPipeline;
        this.properties = properties;
        this.researchExecutor = researchExecutor;
        this.sseHelper = sseHelper;
    }

    // ── Inner record for section planning ────────────────────────────────────────

    record SectionPlan(String title, String description) {}

    // ── Main entry point ─────────────────────────────────────────────────────────

    /**
     * Main entry point: orchestrates multi-section document writing.
     * Phase A (research) -> Phase B (structure) -> Phase C (sequential writing).
     */
    public void orchestrate(String prompt, String systemPrompt,
                             List<ConversationMessage> conversationHistory,
                             String documentContext,
                             SseEmitter emitter) {

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE A: Research (delegated to ResearchPhaseExecutor)
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
            sseHelper.emitClarificationEvent(emitter, partialAnalysis);
            emitter.complete();
            return;
        }

        String effectiveQuery = partialAnalysis.getRewrittenQuery() != null
                ? partialAnalysis.getRewrittenQuery() : prompt;

        // ── Phase A1: Generate research plan ──
        log.info("[LAB] Phase A1: Generating research plan for: {}", effectiveQuery);
        sseHelper.emitStepEvent(emitter, "planning", "running", "Elaboration du plan de recherche...", null);

        List<String> subQuestions = researchExecutor.generateResearchPlan(effectiveQuery);

        sseHelper.emitStepEvent(emitter, "planning", "done", null,
                subQuestions.size() + " sous-questions identifiees");
        log.info("[LAB] Plan: {} sub-questions", subQuestions.size());

        // Emit research plan as thinking
        StringBuilder researchThinking = new StringBuilder();
        String planThinking = researchExecutor.buildPlanThinking(subQuestions);
        researchThinking.append(planThinking);
        sseHelper.emitThinking(emitter, planThinking);

        // ── Phase A2: Web search per sub-question ──
        log.info("[LAB] Phase A2: Executing {} sub-queries", subQuestions.size());
        List<SearchResult> allResults = new ArrayList<>();
        int citationOffset = 0;

        for (int i = 0; i < subQuestions.size(); i++) {
            String sq = subQuestions.get(i);
            String stepLabel = "[" + (i + 1) + "/" + subQuestions.size() + "] " + sseHelper.truncate(sq, 60);
            String stepId = "sq-" + (i + 1);

            sseHelper.emitStepEvent(emitter, stepId, "running", stepLabel, null);

            try {
                List<SearchResult> sqResults = researchExecutor.executeSubQuery(sq, citationOffset);
                allResults.addAll(sqResults);
                citationOffset += sqResults.size();

                if (!allResults.isEmpty()) {
                    sseHelper.emitSourcesEvent(emitter, allResults);
                }

                String sqThinking = researchExecutor.buildSubQueryThinking(sq, i, subQuestions.size(), sqResults);
                researchThinking.append(sqThinking);
                sseHelper.emitThinking(emitter, sqThinking);

                sseHelper.emitStepEvent(emitter, stepId, "done", null, sqResults.size() + " source(s)");
                log.info("[LAB] Sub-query {}/{}: {} sources", i + 1, subQuestions.size(), sqResults.size());
            } catch (Exception e) {
                log.warn("[LAB] Sub-query {}/{} failed: {}", i + 1, subQuestions.size(), e.getMessage());
                String failThinking = researchExecutor.buildSubQueryThinking(sq, i, subQuestions.size(), List.of());
                researchThinking.append(failThinking);
                sseHelper.emitThinking(emitter, failThinking);
                sseHelper.emitStepEvent(emitter, stepId, "done", null, "0 sources (erreur)");
            }
        }

        // ── Phase A2.5: Deduplication + context budget ──
        log.info("[LAB] Phase A2.5: Deduplication ({} raw results)", allResults.size());
        List<SearchResult> deduplicated = researchExecutor.deduplicateByUrl(allResults);
        log.info("[LAB] After dedup: {} unique results", deduplicated.size());

        if (!deduplicated.isEmpty()) {
            sseHelper.emitSourcesEvent(emitter, deduplicated);
        }

        String webSearchContext = !deduplicated.isEmpty()
                ? researchExecutor.buildWebContextPrompt(deduplicated) : null;
        webSearchContext = researchExecutor.applyContextBudget(prompt, conversationHistory, documentContext, webSearchContext);

        // ═══════════════════════════════════════════════════════════════════════
        // PHASE B: Document structure planning
        // ═══════════════════════════════════════════════════════════════════════
        log.info("[LAB] Phase B: Planning document structure");
        sseHelper.emitStepEvent(emitter, "doc-planning", "running", "Planification de la structure du document...", null);

        List<SectionPlan> sections = planDocumentStructure(effectiveQuery, webSearchContext);

        sseHelper.emitStepEvent(emitter, "doc-planning", "done", null,
                sections.size() + " sections planifiees");
        log.info("[LAB] Document plan: {} sections", sections.size());

        // Emit structure as thinking
        String structureThinking = buildStructureThinking(sections);
        researchThinking.append(structureThinking);
        sseHelper.emitThinking(emitter, structureThinking);

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

            sseHelper.emitStepEvent(emitter, stepId, "running",
                    "Redaction: " + section.title(), null);

            // Emit section header as answer content (markdown)
            String sectionHeader = "\n\n## " + section.title() + "\n\n";
            sseHelper.sendEvent(emitter, "answer", "{\"content\":" + sseHelper.quote(sectionHeader) + "}");
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
            sseHelper.emitThinking(emitter, sectionThinkingHeader);

            // Stream this section's content
            final int sectionIndex = i;
            StringBuilder sectionAnswer = new StringBuilder();

            streamingLlmPort.streamThinkAndAnswer(
                    sectionSystemPrompt, sectionPrompt, List.of(),
                    // onThinking
                    chunk -> {
                        if (errorOccurred.get()) return;
                        allThinking.append(chunk.content());
                        sseHelper.sendEvent(emitter, "thinking", "{\"content\":" + sseHelper.quote(chunk.content()) + "}");
                    },
                    // onContent
                    chunk -> {
                        if (errorOccurred.get()) return;
                        sectionAnswer.append(chunk.content());
                        fullDocument.append(chunk.content());
                        sseHelper.sendEvent(emitter, "answer", "{\"content\":" + sseHelper.quote(chunk.content()) + "}");
                    },
                    // onError
                    error -> {
                        log.error("[LAB] Section {} error: {}", sectionIndex + 1, error.getMessage());
                        sseHelper.emitStepEvent(emitter, stepId, "done", null, "Erreur de redaction");
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
                sseHelper.emitStepEvent(emitter, stepId, "done", null, "Section terminee");
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // FINAL: Emit done event and complete
        // ═══════════════════════════════════════════════════════════════════════

        if (!finalSources.isEmpty()) {
            sseHelper.emitSourcesEvent(emitter, finalSources);
        }

        String donePayload = sseHelper.buildDonePayload(
                fullDocument.toString(),
                allThinking.toString(),
                finalSources, true);
        sseHelper.sendEvent(emitter, "done", donePayload);
        emitter.complete();
        log.info("[LAB] Stream completed — full document: {} chars", fullDocument.length());
    }

    // ── Phase B helpers (document structure) ──────────────────────────────────────

    private List<SectionPlan> planDocumentStructure(String query, String webSearchContext) {
        try {
            String context = "Question: " + query;
            if (webSearchContext != null) {
                context += "\n\nSources disponibles (resume):\n" + sseHelper.truncate(webSearchContext, 2000);
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
            String json = sseHelper.extractJson(raw);
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

    // ── Thinking content builder (unique to Lab) ─────────────────────────────────

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
}
