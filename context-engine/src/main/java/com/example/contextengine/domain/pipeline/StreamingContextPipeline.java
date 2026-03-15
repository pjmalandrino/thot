package com.example.contextengine.domain.pipeline;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.port.out.FeatureFlagPort;
import com.example.contextengine.infrastructure.config.ContextEngineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Streaming variant of ContextPipeline.
 * Emits SSE step events as each pipeline step starts and completes.
 */
@Component
public class StreamingContextPipeline {

    private static final Logger log = LoggerFactory.getLogger(StreamingContextPipeline.class);

    private static final Map<String, String> STEP_LABELS = Map.of(
            "vagueness-detection", "Analyse de votre question...",
            "query-rewriting", "Reformulation pour plus de precision...",
            "auto-web-search", "Detection du besoin de recherche...",
            "web-search", "Recherche de sources sur le web...",
            "web-search-relevance", "Verification de la pertinence des sources...",
            "context-budget", "Optimisation du contexte..."
    );

    private final List<ContextStep> steps;
    private final FeatureFlagPort featureFlagPort;
    private final double minConfidence;
    private final boolean failOpen;

    public StreamingContextPipeline(List<ContextStep> steps,
                                    FeatureFlagPort featureFlagPort,
                                    ContextEngineProperties properties) {
        this.steps = steps;
        this.featureFlagPort = featureFlagPort;
        this.minConfidence = properties.getMinConfidence();
        this.failOpen = properties.getFallbackPolicy() ==
                ContextEngineProperties.FallbackPolicy.CONTINUE;
    }

    /**
     * Runs the pipeline, emitting SSE events for each step.
     * Returns the final ContextAnalysis (same as ContextPipeline.run()).
     */
    public ContextAnalysis runStreaming(PipelineContext context, SseEmitter emitter) {
        for (ContextStep step : steps) {
            String featureName = step.featureName();

            if (!featureFlagPort.isEnabled(featureName)) {
                log.debug("[STREAMING-PIPELINE] Skipping '{}' (disabled)", featureName);
                continue;
            }

            emitStepEvent(emitter, featureName, "running",
                    STEP_LABELS.getOrDefault(featureName, featureName), null);

            log.info("[STREAMING-PIPELINE] Running step '{}'", featureName);
            StepResult result = step.execute(context);

            if (!result.shouldContinue()) {
                if (result.getConfidence() < minConfidence && failOpen) {
                    log.warn("[STREAMING-PIPELINE] '{}' low confidence, failing open", featureName);
                    emitStepEvent(emitter, featureName, "skipped", null, "Confiance insuffisante");
                    continue;
                }

                emitStepEvent(emitter, featureName, "interrupted", null, result.getMessage());
                log.info("[STREAMING-PIPELINE] '{}' interrupted pipeline", featureName);
                return ContextAnalysis.clarificationNeeded(
                        result.getMessage(), result.getSuggestions(), result.getConfidence());
            }

            emitStepEvent(emitter, featureName, "done", null, summarizeStep(featureName, context));
            log.info("[STREAMING-PIPELINE] Step '{}' passed", featureName);
        }

        return ContextAnalysis.continueWith(
                context.getRewrittenQuery(),
                context.getWebSearchResults(),
                context.getWebSearchContext(),
                context.isAutoWebSearchTriggered(),
                context.getTokenBudget());
    }

    /**
     * Runs only the pipeline steps whose featureName is in allowedSteps.
     * Other steps are silently skipped. Used by DeepResearchOrchestrator
     * to run vagueness-detection and query-rewriting without web search.
     */
    public ContextAnalysis runStreamingPartial(PipelineContext context, SseEmitter emitter,
                                                Set<String> allowedSteps) {
        for (ContextStep step : steps) {
            String featureName = step.featureName();

            if (!allowedSteps.contains(featureName)) {
                continue;
            }

            if (!featureFlagPort.isEnabled(featureName)) {
                log.debug("[STREAMING-PIPELINE] Skipping '{}' (disabled)", featureName);
                continue;
            }

            emitStepEvent(emitter, featureName, "running",
                    STEP_LABELS.getOrDefault(featureName, featureName), null);

            log.info("[STREAMING-PIPELINE-PARTIAL] Running step '{}'", featureName);
            StepResult result = step.execute(context);

            if (!result.shouldContinue()) {
                if (result.getConfidence() < minConfidence && failOpen) {
                    log.warn("[STREAMING-PIPELINE-PARTIAL] '{}' low confidence, failing open", featureName);
                    emitStepEvent(emitter, featureName, "skipped", null, "Confiance insuffisante");
                    continue;
                }

                emitStepEvent(emitter, featureName, "interrupted", null, result.getMessage());
                log.info("[STREAMING-PIPELINE-PARTIAL] '{}' interrupted pipeline", featureName);
                return ContextAnalysis.clarificationNeeded(
                        result.getMessage(), result.getSuggestions(), result.getConfidence());
            }

            emitStepEvent(emitter, featureName, "done", null, summarizeStep(featureName, context));
            log.info("[STREAMING-PIPELINE-PARTIAL] Step '{}' passed", featureName);
        }

        return ContextAnalysis.continueWith(
                context.getRewrittenQuery(),
                context.getWebSearchResults(),
                context.getWebSearchContext(),
                context.isAutoWebSearchTriggered(),
                context.getTokenBudget());
    }

    private void emitStepEvent(SseEmitter emitter, String stepId, String status,
                                String label, String detail) {
        try {
            StringBuilder json = new StringBuilder("{");
            json.append("\"stepId\":\"").append(stepId).append("\",");
            json.append("\"status\":\"").append(status).append("\"");
            if (label != null) json.append(",\"label\":\"").append(escapeJson(label)).append("\"");
            if (detail != null) json.append(",\"detail\":\"").append(escapeJson(detail)).append("\"");
            json.append("}");

            emitter.send(SseEmitter.event()
                    .name("step")
                    .data(json.toString()));
        } catch (IOException e) {
            log.warn("[STREAMING-PIPELINE] Failed to emit step event: {}", e.getMessage());
        }
    }

    private String summarizeStep(String featureName, PipelineContext context) {
        return switch (featureName) {
            case "query-rewriting" -> context.getRewrittenQuery() != null
                    ? "Requete reformulee" : "Pas de reformulation necessaire";
            case "auto-web-search" -> context.isAutoWebSearchTriggered()
                    ? "Recherche web declenchee" : "Pas besoin de recherche web";
            case "web-search" -> context.getWebSearchResults() != null
                    ? context.getWebSearchResults().size() + " source(s) trouvee(s)" : "Aucune source";
            case "web-search-relevance" -> context.getWebSearchResults() != null
                    ? context.getWebSearchResults().size() + " source(s) pertinente(s)" : "";
            case "context-budget" -> "Contexte optimise";
            default -> "OK";
        };
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
