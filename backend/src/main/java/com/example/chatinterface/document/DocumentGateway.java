package com.example.chatinterface.document;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstraction for document parsing.
 * Switching provider = new implementation (Google Drive, S3, etc.), nothing else changes.
 */
public interface DocumentGateway {

    DocumentParseResult parse(MultipartFile file);

    /**
     * Parse raw bytes (e.g., from Google Drive download).
     */
    DocumentParseResult parse(byte[] content, String filename);
}
