package com.example.contextengine.domain.model;

import java.util.List;
import java.util.Map;

public class ContextAnalysis {

    private final String status;
    private final Double confidence;
    private final String clarificationMessage;
    private final List<String> suggestions;
    private final String rewrittenQuery;
    private final List<SearchResult> webSearchResults;
    private final String webSearchContext;
    private final boolean autoWebSearchTriggered;
    private final Map<String, Integer> tokenUsage;

    private ContextAnalysis(String status, Double confidence, String clarificationMessage,
                            List<String> suggestions, String rewrittenQuery,
                            List<SearchResult> webSearchResults, String webSearchContext,
                            boolean autoWebSearchTriggered, Map<String, Integer> tokenUsage) {
        this.status = status;
        this.confidence = confidence;
        this.clarificationMessage = clarificationMessage;
        this.suggestions = suggestions;
        this.rewrittenQuery = rewrittenQuery;
        this.webSearchResults = webSearchResults;
        this.webSearchContext = webSearchContext;
        this.autoWebSearchTriggered = autoWebSearchTriggered;
        this.tokenUsage = tokenUsage;
    }

    public static ContextAnalysis continueWith(String rewrittenQuery,
                                                List<SearchResult> webSearchResults,
                                                String webSearchContext,
                                                boolean autoWebSearchTriggered,
                                                Map<String, Integer> tokenUsage) {
        return new ContextAnalysis("continue", 1.0, null, null,
                rewrittenQuery, webSearchResults, webSearchContext,
                autoWebSearchTriggered, tokenUsage);
    }

    public static ContextAnalysis clarificationNeeded(String message, List<String> suggestions, double confidence) {
        return new ContextAnalysis("clarification_needed", confidence, message, suggestions,
                null, List.of(), null, false, Map.of());
    }

    public String getStatus() { return status; }
    public Double getConfidence() { return confidence; }
    public String getClarificationMessage() { return clarificationMessage; }
    public List<String> getSuggestions() { return suggestions; }
    public String getRewrittenQuery() { return rewrittenQuery; }
    public List<SearchResult> getWebSearchResults() { return webSearchResults; }
    public String getWebSearchContext() { return webSearchContext; }
    public boolean isAutoWebSearchTriggered() { return autoWebSearchTriggered; }
    public Map<String, Integer> getTokenUsage() { return tokenUsage; }

    public boolean isContinue() { return "continue".equals(status); }
}
