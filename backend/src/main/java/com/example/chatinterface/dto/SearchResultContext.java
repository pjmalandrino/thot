package com.example.chatinterface.dto;

import java.time.Instant;

public class SearchResultContext {

    private String citationId;          // "[1]", "[2]", etc.
    private String sourceUrl;
    private String sourceTitle;
    private String providerName;        // "tavily"
    private Instant retrievedAt;
    private String originatingQuery;
    private int searchRank;
    private Double optionalSearchScore;
    private String extractedText;

    public SearchResultContext(String citationId, String sourceUrl, String sourceTitle,
                               String providerName, Instant retrievedAt, String originatingQuery,
                               int searchRank, Double optionalSearchScore, String extractedText) {
        this.citationId = citationId;
        this.sourceUrl = sourceUrl;
        this.sourceTitle = sourceTitle;
        this.providerName = providerName;
        this.retrievedAt = retrievedAt;
        this.originatingQuery = originatingQuery;
        this.searchRank = searchRank;
        this.optionalSearchScore = optionalSearchScore;
        this.extractedText = extractedText;
    }

    public String getCitationId() { return citationId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceTitle() { return sourceTitle; }
    public String getProviderName() { return providerName; }
    public Instant getRetrievedAt() { return retrievedAt; }
    public String getOriginatingQuery() { return originatingQuery; }
    public int getSearchRank() { return searchRank; }
    public Double getOptionalSearchScore() { return optionalSearchScore; }
    public String getExtractedText() { return extractedText; }
}
