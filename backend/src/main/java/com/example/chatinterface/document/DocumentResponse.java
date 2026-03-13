package com.example.chatinterface.document;

import java.time.LocalDateTime;

public class DocumentResponse {

    private Long id;
    private String filename;
    private String contentType;
    private Integer pageCount;
    private int charCount;
    private LocalDateTime uploadedAt;

    public static DocumentResponse from(Document doc) {
        DocumentResponse r = new DocumentResponse();
        r.id = doc.getId();
        r.filename = doc.getFilename();
        r.contentType = doc.getContentType();
        r.pageCount = doc.getPageCount();
        r.charCount = doc.getCharCount();
        r.uploadedAt = doc.getUploadedAt();
        return r;
    }

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public Integer getPageCount() { return pageCount; }
    public int getCharCount() { return charCount; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
