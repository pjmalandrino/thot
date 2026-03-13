package com.example.chatinterface.document;

import com.example.chatinterface.conversation.Conversation;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @JsonIgnore
    private Conversation conversation;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public Document() {}

    public Document(Conversation conversation, String filename, String contentType,
                    Integer pageCount, int charCount, String extractedText) {
        this.conversation = conversation;
        this.filename = filename;
        this.contentType = contentType;
        this.pageCount = pageCount;
        this.charCount = charCount;
        this.extractedText = extractedText;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public Integer getPageCount() { return pageCount; }
    public int getCharCount() { return charCount; }
    public String getExtractedText() { return extractedText; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
