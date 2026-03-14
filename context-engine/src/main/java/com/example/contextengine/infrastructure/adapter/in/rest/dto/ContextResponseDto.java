package com.example.contextengine.infrastructure.adapter.in.rest.dto;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.SearchResult;

import java.util.List;
import java.util.Map;

public class ContextResponseDto {

    private String status;
    private Double confidence;
    private String clarificationMessage;
    private List<String> suggestions;
    private String rewrittenQuery;
    private List<WebSearchResultDto> webSearchResults;
    private String webSearchContext;
    private boolean autoWebSearchTriggered;
    private Map<String, Integer> tokenUsage;

    public record WebSearchResultDto(String citationId, String sourceUrl, String sourceTitle, String extractedText) {
        public static WebSearchResultDto from(SearchResult r) {
            return new WebSearchResultDto(r.citationId(), r.sourceUrl(), r.sourceTitle(), r.extractedText());
        }
    }

    public static ContextResponseDto from(ContextAnalysis analysis) {
        ContextResponseDto dto = new ContextResponseDto();
        dto.status = analysis.getStatus();
        dto.confidence = analysis.getConfidence();
        dto.clarificationMessage = analysis.getClarificationMessage();
        dto.suggestions = analysis.getSuggestions();
        dto.rewrittenQuery = analysis.getRewrittenQuery();
        dto.webSearchResults = analysis.getWebSearchResults() != null
                ? analysis.getWebSearchResults().stream().map(WebSearchResultDto::from).toList()
                : null;
        dto.webSearchContext = analysis.getWebSearchContext();
        dto.autoWebSearchTriggered = analysis.isAutoWebSearchTriggered();
        dto.tokenUsage = analysis.getTokenUsage();
        return dto;
    }

    public String getStatus() { return status; }
    public Double getConfidence() { return confidence; }
    public String getClarificationMessage() { return clarificationMessage; }
    public List<String> getSuggestions() { return suggestions; }
    public String getRewrittenQuery() { return rewrittenQuery; }
    public List<WebSearchResultDto> getWebSearchResults() { return webSearchResults; }
    public String getWebSearchContext() { return webSearchContext; }
    public boolean isAutoWebSearchTriggered() { return autoWebSearchTriggered; }
    public Map<String, Integer> getTokenUsage() { return tokenUsage; }
}
