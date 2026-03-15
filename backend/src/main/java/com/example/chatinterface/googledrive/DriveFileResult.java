package com.example.chatinterface.googledrive;

public record DriveFileResult(
        String fileId,
        String name,
        String mimeType,
        String modifiedTime,
        String webViewLink,
        Long size
) {}
