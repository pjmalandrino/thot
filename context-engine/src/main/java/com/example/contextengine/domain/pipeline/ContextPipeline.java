package com.example.contextengine.domain.pipeline;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.port.out.FeatureFlagPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContextPipeline {

    private static final Logger log = LoggerFactory.getLogger(ContextPipeline.class);

    private final List<ContextStep> steps;
    private final FeatureFlagPort featureFlagPort;
    private final double minConfidence;
    private final boolean failOpen;

    public ContextPipeline(List<ContextStep> steps,
                           FeatureFlagPort featureFlagPort,
                           com.example.contextengine.infrastructure.config.ContextEngineProperties properties) {
        this.steps = steps;
        this.featureFlagPort = featureFlagPort;
        this.minConfidence = properties.getMinConfidence();
        this.failOpen = properties.getFallbackPolicy() ==
                com.example.contextengine.infrastructure.config.ContextEngineProperties.FallbackPolicy.CONTINUE;
        log.info("[PIPELINE] Initialized with {} step(s): {} | minConfidence={}, failOpen={}",
                steps.size(),
                steps.stream().map(ContextStep::featureName).toList(),
                minConfidence, failOpen);
    }

    public ContextAnalysis run(PipelineContext context) {
        for (ContextStep step : steps) {
            if (!featureFlagPort.isEnabled(step.featureName())) {
                log.debug("[PIPELINE] Skipping '{}' (feature disabled)", step.featureName());
                continue;
            }

            log.info("[PIPELINE] Running step '{}'", step.featureName());
            StepResult result = step.execute(context);

            if (!result.shouldContinue()) {
                if (result.getConfidence() < minConfidence && failOpen) {
                    log.warn("[PIPELINE] '{}' interrupted with low confidence ({} < {}), failing open",
                            step.featureName(), result.getConfidence(), minConfidence);
                    continue;
                }
                log.info("[PIPELINE] '{}' interrupted pipeline: {} (confidence={})",
                        step.featureName(), result.getType(), result.getConfidence());
                return ContextAnalysis.clarificationNeeded(
                        result.getMessage(), result.getSuggestions(), result.getConfidence());
            }

            log.info("[PIPELINE] Step '{}' passed", step.featureName());
        }

        return ContextAnalysis.continueWith(
                context.getRewrittenQuery(),
                context.getWebSearchResults(),
                context.getWebSearchContext(),
                context.getDriveDocumentContext(),
                context.isAutoWebSearchTriggered(),
                context.getTokenBudget());
    }
}
