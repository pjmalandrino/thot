package com.example.chatinterface.googledrive;

import java.util.List;

public record DriveSearchResult(
        List<DriveDocumentResult> documents,
        String driveDocumentContext
) {}
