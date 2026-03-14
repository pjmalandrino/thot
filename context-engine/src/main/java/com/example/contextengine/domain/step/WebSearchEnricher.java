package com.example.contextengine.domain.step;

import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.pipeline.ContextStep;
import com.example.contextengine.domain.pipeline.PipelineContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(30)
public class WebSearchEnricher implements ContextStep {

    private static final Logger log = LoggerFactory.getLogger(WebSearchEnricher.class);

    @Override
    public String featureName() {
        return "web-search";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        if (!context.isWebSearchRequested()) {
            log.debug("[WEB-SEARCH] Not requested, skipping");
            return StepResult.continueProcessing();
        }

        try {
            String query = context.getEffectiveQuery();
            log.info("[WEB-SEARCH] Searching for: '{}'", query);

            List<SearchResult> results = context.getWebSearchPort().searchAndExtract(query);
            context.setWebSearchResults(results);

            String contextPrompt = context.getWebSearchPort().buildContextPrompt(results);
            context.setWebSearchContext(contextPrompt);

            log.info("[WEB-SEARCH] Got {} results", results.size());
            return StepResult.continueProcessing();
        } catch (Exception e) {
            log.warn("[WEB-SEARCH] Error, failing open: {}", e.getMessage());
            return StepResult.continueProcessing();
        }
    }
}
