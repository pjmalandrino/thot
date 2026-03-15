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
import com.example.contextengine.infrastructure.config.ContextEngineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
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

    private final LlmPort llmPort;
    private final WebSearchPort webSearchPort;
    private final StreamingLlmPort streamingLlmPort;
    private final StreamingContextPipeline streamingPipeline;
    private final ContextEngineProperties properties;
    private final ResearchPhaseExecutor researchExecutor;
    private final SseEmitterHelper sseHelper;

    public DeepResearchOrchestrator(LlmPort llmPort,
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
            sseHelper.emitClarificationEvent(emitter, partialAnalysis);
            emitter.complete();
            return;
        }

        String effectiveQuery = partialAnalysis.getRewrittenQuery() != null
                ? partialAnalysis.getRewrittenQuery() : prompt;

        // ── Phase 1: Generate research plan ──
        log.info("[DEEP-RESEARCH] Phase 1: Generating research plan for: {}", effectiveQuery);
        sseHelper.emitStepEvent(emitter, "planning", "running", "Elaboration du plan de recherche...", null);

        List<String> subQuestions = researchExecutor.generateResearchPlan(effectiveQuery);

        sseHelper.emitStepEvent(emitter, "planning", "done", null,
                subQuestions.size() + " sous-questions identifiees");
        log.info("[DEEP-RESEARCH] Plan: {} sub-questions", subQuestions.size());

        // Emit research plan as thinking (Perplexity-style progressive thinking)
        StringBuilder researchThinking = new StringBuilder();
        String planThinking = researchExecutor.buildPlanThinking(subQuestions);
        researchThinking.append(planThinking);
        sseHelper.emitThinking(emitter, planThinking);

        // ── Phase 2: Web search per sub-question ──
        log.info("[DEEP-RESEARCH] Phase 2: Executing {} sub-queries", subQuestions.size());
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

                // Emit progressive sources
                if (!allResults.isEmpty()) {
                    sseHelper.emitSourcesEvent(emitter, allResults);
                }

                // Emit per-sub-query thinking (Perplexity-style findings)
                String sqThinking = researchExecutor.buildSubQueryThinking(sq, i, subQuestions.size(), sqResults);
                researchThinking.append(sqThinking);
                sseHelper.emitThinking(emitter, sqThinking);

                sseHelper.emitStepEvent(emitter, stepId, "done", null, sqResults.size() + " source(s)");
                log.info("[DEEP-RESEARCH] Sub-query {}/{}: {} sources", i + 1, subQuestions.size(), sqResults.size());
            } catch (Exception e) {
                log.warn("[DEEP-RESEARCH] Sub-query {}/{} failed: {}", i + 1, subQuestions.size(), e.getMessage());
                String failThinking = researchExecutor.buildSubQueryThinking(sq, i, subQuestions.size(), List.of());
                researchThinking.append(failThinking);
                sseHelper.emitThinking(emitter, failThinking);
                sseHelper.emitStepEvent(emitter, stepId, "done", null, "0 sources (erreur)");
            }
        }

        // ── Phase 2.5: Deduplication + context budget ──
        log.info("[DEEP-RESEARCH] Phase 2.5: Deduplication ({} raw results)", allResults.size());
        List<SearchResult> deduplicated = researchExecutor.deduplicateByUrl(allResults);
        log.info("[DEEP-RESEARCH] After dedup: {} unique results", deduplicated.size());

        // Emit final deduplicated sources
        if (!deduplicated.isEmpty()) {
            sseHelper.emitSourcesEvent(emitter, deduplicated);
        }

        // Build web context and apply budget
        String webSearchContext = !deduplicated.isEmpty()
                ? researchExecutor.buildWebContextPrompt(deduplicated) : null;
        webSearchContext = researchExecutor.applyContextBudget(prompt, conversationHistory, documentContext, webSearchContext);

        // ── Phase 3: Streaming synthesis ──
        log.info("[DEEP-RESEARCH] Phase 3: Streaming synthesis with {} sources", deduplicated.size());
        sseHelper.emitStepEvent(emitter, "synthesis", "running", "Synthese en cours...", null);

        // Emit synthesis header as thinking
        String synthesisHeader = "\n---\n## Synthese\n\n";
        researchThinking.append(synthesisHeader);
        sseHelper.emitThinking(emitter, synthesisHeader);

        String fullSystemPrompt = buildResearchSystemPrompt(systemPrompt, documentContext, webSearchContext);
        StringBuilder answerAccumulator = new StringBuilder();
        List<SearchResult> finalSources = deduplicated;

        streamingLlmPort.streamThinkAndAnswer(
                fullSystemPrompt, effectiveQuery, conversationHistory,
                thinking -> {
                    researchThinking.append(thinking.content());
                    sseHelper.sendEvent(emitter, "thinking", "{\"content\":" + sseHelper.quote(thinking.content()) + "}");
                },
                chunk -> {
                    answerAccumulator.append(chunk.content());
                    sseHelper.sendEvent(emitter, "answer", "{\"content\":" + sseHelper.quote(chunk.content()) + "}");
                },
                error -> {
                    log.error("[DEEP-RESEARCH] LLM synthesis error: {}", error.getMessage());
                    sseHelper.sendEvent(emitter, "error", "{\"message\":" + sseHelper.quote(error.getMessage()) + "}");
                    emitter.complete();
                },
                () -> {
                    sseHelper.emitStepEvent(emitter, "synthesis", "done", null, "Synthese terminee");

                    // Emit final sources again (for consistency)
                    if (!finalSources.isEmpty()) {
                        sseHelper.emitSourcesEvent(emitter, finalSources);
                    }

                    String donePayload = sseHelper.buildDonePayload(
                            answerAccumulator.toString(),
                            researchThinking.toString(),
                            finalSources, true);
                    sseHelper.sendEvent(emitter, "done", donePayload);
                    emitter.complete();
                    log.info("[DEEP-RESEARCH] Stream completed");
                }
        );
    }

    // ── Unique to DeepResearch ────────────────────────────────────────────────

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
}
