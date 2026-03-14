package com.example.contextengine.domain.model;

import java.util.List;

public class ContextAnalysis {

    private final String status;
    private final Double confidence;
    private final String clarificationMessage;
    private final List<String> suggestions;
    private final String rewrittenQuery;
    private final List<SearchResult> webSearchResults;
    private final String webSearchContext;

    private ContextAnalysis(String status, Double confidence, String clarificationMessage,
                            List<String> suggestions, String rewrittenQuery,
                            List<SearchResult> webSearchResults, String webSearchContext) {
        this.status = status;
        this.confidence = confidence;
        this.clarificationMessage = clarificationMessage;
        this.suggestions = suggestions;
        this.rewrittenQuery = rewrittenQuery;
        this.webSearchResults = webSearchResults;
        this.webSearchContext = webSearchContext;
    }

    public static ContextAnalysis continueWith(String rewrittenQuery,
                                                List<SearchResult> webSearchResults,
                                                String webSearchContext) {
        return new ContextAnalysis("continue", 1.0, null, null,
                rewrittenQuery, webSearchResults, webSearchContext);
    }

    public static ContextAnalysis clarificationNeeded(String message, List<String> suggestions, double confidence) {
        return new ContextAnalysis("clarification_needed", confidence, message, suggestions,
                null, List.of(), null);
    }

    public String getStatus() { return status; }
    public Double getConfidence() { return confidence; }
    public String getClarificationMessage() { return clarificationMessage; }
    public List<String> getSuggestions() { return suggestions; }
    public String getRewrittenQuery() { return rewrittenQuery; }
    public List<SearchResult> getWebSearchResults() { return webSearchResults; }
    public String getWebSearchContext() { return webSearchContext; }

    public boolean isContinue() { return "continue".equals(status); }
}
