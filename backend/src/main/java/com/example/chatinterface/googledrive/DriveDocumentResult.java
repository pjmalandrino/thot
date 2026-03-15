package com.example.chatinterface.googledrive;

public record DriveDocumentResult(
        String fileId,
        String fileName,
        String mimeType,
        String webViewLink,
        String extractedText
) {}
