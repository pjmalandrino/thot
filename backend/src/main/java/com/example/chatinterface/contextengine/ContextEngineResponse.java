package com.example.chatinterface.contextengine;

import java.util.List;
import java.util.Map;

public class ContextEngineResponse {

    private String status;
    private Double confidence;
    private String clarificationMessage;
    private List<String> suggestions;
    private String rewrittenQuery;
    private List<WebSearchResultDto> webSearchResults;
    private String webSearchContext;
    private String driveDocumentContext;
    private boolean autoWebSearchTriggered;
    private Map<String, Integer> tokenUsage;

    public record WebSearchResultDto(String citationId, String sourceUrl, String sourceTitle, String extractedText) {}

    public static ContextEngineResponse failOpen() {
        ContextEngineResponse r = new ContextEngineResponse();
        r.status = "continue";
        r.confidence = 1.0;
        return r;
    }

    public boolean isContinue() { return "continue".equals(status); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getClarificationMessage() { return clarificationMessage; }
    public void setClarificationMessage(String clarificationMessage) { this.clarificationMessage = clarificationMessage; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
    public String getRewrittenQuery() { return rewrittenQuery; }
    public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }
    public List<WebSearchResultDto> getWebSearchResults() { return webSearchResults; }
    public void setWebSearchResults(List<WebSearchResultDto> webSearchResults) { this.webSearchResults = webSearchResults; }
    public String getWebSearchContext() { return webSearchContext; }
    public void setWebSearchContext(String webSearchContext) { this.webSearchContext = webSearchContext; }
    public String getDriveDocumentContext() { return driveDocumentContext; }
    public void setDriveDocumentContext(String driveDocumentContext) { this.driveDocumentContext = driveDocumentContext; }
    public boolean isAutoWebSearchTriggered() { return autoWebSearchTriggered; }
    public void setAutoWebSearchTriggered(boolean autoWebSearchTriggered) { this.autoWebSearchTriggered = autoWebSearchTriggered; }
    public Map<String, Integer> getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(Map<String, Integer> tokenUsage) { this.tokenUsage = tokenUsage; }
}
