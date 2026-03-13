package com.example.chatinterface.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Orchestrateur du pipeline de context engineering.
 * Injecte automatiquement tous les @Component implementant ContextStep (auto-discovery Spring).
 * Execute les steps actives dans l'ordre de leur @Order, s'arrete au premier INTERRUPT.
 * Applique la politique de confiance : si confiance < seuil et policy = CONTINUE → fail-open.
 */
@Component
public class ContextPipeline {

    private static final Logger log = LoggerFactory.getLogger(ContextPipeline.class);

    private final List<ContextStep> steps;
    private final ContextProperties properties;

    public ContextPipeline(List<ContextStep> steps, ContextProperties properties) {
        this.steps = steps;
        this.properties = properties;
        log.info("[CONTEXT] Pipeline initialized with {} step(s): {} | minConfidence={}, fallback={}",
                steps.size(),
                steps.stream().map(ContextStep::name).toList(),
                properties.getMinConfidence(),
                properties.getFallbackPolicy());
    }

    /**
     * Execute le pipeline sur un prompt.
     *
     * @param prompt       le message de l'utilisateur
     * @param llm          le port LLM adapte
     * @param enabledSteps les noms des steps a executer (filtre)
     * @return le premier resultat INTERRUPT (si confiance suffisante), ou CONTINUE
     */
    public ContextStepResult run(String prompt, ContextLlm llm, Set<String> enabledSteps) {
        for (ContextStep step : steps) {
            if (!enabledSteps.contains(step.name())) {
                log.debug("[CONTEXT] Skipping step '{}' (not enabled)", step.name());
                continue;
            }

            log.info("[CONTEXT] Running step '{}'", step.name());
            ContextStepResult result = step.execute(prompt, llm);

            if (!result.shouldContinue()) {
                // Politique de confiance centralisee
                if (result.getConfidence() < properties.getMinConfidence()
                        && properties.getFallbackPolicy() == ContextProperties.FallbackPolicy.CONTINUE) {
                    log.warn("[CONTEXT] Step '{}' interrupted with low confidence ({} < {}), failing open",
                            step.name(), result.getConfidence(), properties.getMinConfidence());
                    return ContextStepResult.continueProcessing();
                }

                log.info("[CONTEXT] Step '{}' interrupted: {} (confidence={})",
                        step.name(), result.getType(), result.getConfidence());
                return result;
            }

            log.info("[CONTEXT] Step '{}' passed (confidence={})", step.name(), result.getConfidence());
        }

        return ContextStepResult.continueProcessing();
    }
}
