package com.example.chatinterface.document;

public class DocumentParseResult {

    private final String filename;
    private final String contentType;
    private final Integer pageCount;
    private final int charCount;
    private final String extractedText;

    public DocumentParseResult(String filename, String contentType,
                                Integer pageCount, int charCount, String extractedText) {
        this.filename = filename;
        this.contentType = contentType;
        this.pageCount = pageCount;
        this.charCount = charCount;
        this.extractedText = extractedText;
    }

    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public Integer getPageCount() { return pageCount; }
    public int getCharCount() { return charCount; }
    public String getExtractedText() { return extractedText; }
}
