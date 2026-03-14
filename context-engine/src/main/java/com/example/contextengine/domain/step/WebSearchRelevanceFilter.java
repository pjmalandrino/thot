package com.example.contextengine.domain.step;

import com.example.contextengine.domain.model.SearchResult;
import com.example.contextengine.domain.model.StepResult;
import com.example.contextengine.domain.pipeline.ContextStep;
import com.example.contextengine.domain.pipeline.PipelineContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(35)
public class WebSearchRelevanceFilter implements ContextStep {

    private static final Logger log = LoggerFactory.getLogger(WebSearchRelevanceFilter.class);
    private static final double MIN_RELEVANCE_SCORE = 0.15;

    @Override
    public String featureName() {
        return "web-search-relevance";
    }

    @Override
    public StepResult execute(PipelineContext context) {
        List<SearchResult> results = context.getWebSearchResults();
        if (results == null || results.isEmpty()) {
            log.debug("[RELEVANCE-FILTER] No results to filter");
            return StepResult.continueProcessing();
        }

        try {
            String query = context.getEffectiveQuery();
            Set<String> queryTerms = tokenize(query);

            List<SearchResult> filtered = results.stream()
                    .filter(r -> {
                        double score = computeRelevanceScore(queryTerms, r);
                        log.debug("[RELEVANCE-FILTER] {} score={}", r.citationId(), score);
                        return score >= MIN_RELEVANCE_SCORE;
                    })
                    .toList();

            if (filtered.isEmpty()) {
                log.info("[RELEVANCE-FILTER] All results below threshold, keeping originals");
                return StepResult.continueProcessing();
            }

            if (filtered.size() < results.size()) {
                log.info("[RELEVANCE-FILTER] Filtered {} -> {} results", results.size(), filtered.size());

                // re-number citations
                List<SearchResult> renumbered = new java.util.ArrayList<>();
                for (int i = 0; i < filtered.size(); i++) {
                    SearchResult r = filtered.get(i);
                    renumbered.add(new SearchResult(
                            "[" + (i + 1) + "]", r.sourceUrl(), r.sourceTitle(), r.extractedText()));
                }

                context.setWebSearchResults(renumbered);
                context.setWebSearchContext(
                        context.getWebSearchPort().buildContextPrompt(renumbered));
            } else {
                log.info("[RELEVANCE-FILTER] All {} results relevant", results.size());
            }

            return StepResult.continueProcessing();
        } catch (Exception e) {
            log.warn("[RELEVANCE-FILTER] Error, failing open: {}", e.getMessage());
            return StepResult.continueProcessing();
        }
    }

    double computeRelevanceScore(Set<String> queryTerms, SearchResult result) {
        if (queryTerms.isEmpty()) return 1.0;

        String content = (result.sourceTitle() + " " + result.extractedText()).toLowerCase();
        Set<String> contentTerms = tokenize(content);

        long matchingTerms = queryTerms.stream()
                .filter(contentTerms::contains)
                .count();

        return (double) matchingTerms / queryTerms.size();
    }

    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.toLowerCase().split("[\\s\\p{Punct}]+"))
                .filter(w -> w.length() > 2) // skip short words (le, de, a, ...)
                .collect(Collectors.toSet());
    }
}
