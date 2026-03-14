package com.example.chatinterface.conversation;

public class SourceInfo {

    private String citationId;
    private String sourceUrl;
    private String sourceTitle;
    private String extractedText;

    public SourceInfo() {}

    public SourceInfo(String citationId, String sourceUrl, String sourceTitle) {
        this.citationId = citationId;
        this.sourceUrl = sourceUrl;
        this.sourceTitle = sourceTitle;
    }

    public SourceInfo(String citationId, String sourceUrl, String sourceTitle, String extractedText) {
        this(citationId, sourceUrl, sourceTitle);
        this.extractedText = extractedText;
    }

    public String getCitationId() { return citationId; }
    public void setCitationId(String citationId) { this.citationId = citationId; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
}
