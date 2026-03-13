package com.example.chatinterface.websearch;

public class SearchResult {

    private final String citationId;
    private final String sourceUrl;
    private final String sourceTitle;
    private final String extractedText;

    public SearchResult(String citationId, String sourceUrl, String sourceTitle, String extractedText) {
        this.citationId = citationId;
        this.sourceUrl = sourceUrl;
        this.sourceTitle = sourceTitle;
        this.extractedText = extractedText;
    }

    public String getCitationId() { return citationId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceTitle() { return sourceTitle; }
    public String getExtractedText() { return extractedText; }
}
