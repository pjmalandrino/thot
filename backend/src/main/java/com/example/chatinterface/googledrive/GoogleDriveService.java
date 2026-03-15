package com.example.chatinterface.googledrive;

import com.example.chatinterface.document.DocumentGateway;
import com.example.chatinterface.document.DocumentParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Orchestrates Drive search + text extraction.
 * Handles the routing between native export (Workspace docs) and OCR (binary files).
 */
@Service
public class GoogleDriveService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);

    // Google Workspace MIME types that support files.export
    private static final String MIME_GOOGLE_DOC = "application/vnd.google-apps.document";
    private static final String MIME_GOOGLE_SHEET = "application/vnd.google-apps.spreadsheet";
    private static final String MIME_GOOGLE_SLIDES = "application/vnd.google-apps.presentation";
    private static final Set<String> GOOGLE_WORKSPACE_TYPES = Set.of(
            MIME_GOOGLE_DOC, MIME_GOOGLE_SHEET, MIME_GOOGLE_SLIDES
    );

    // Binary types that need download + OCR
    private static final Set<String> OCR_TYPES = Set.of(
            "application/pdf",
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/tiff"
    );

    private final GoogleDriveTokenService tokenService;
    private final GoogleDriveClient driveClient;
    private final DocumentGateway documentGateway;
    private final GoogleDriveProperties properties;

    public GoogleDriveService(GoogleDriveTokenService tokenService,
                              GoogleDriveClient driveClient,
                              DocumentGateway documentGateway,
                              GoogleDriveProperties properties) {
        this.tokenService = tokenService;
        this.driveClient = driveClient;
        this.documentGateway = documentGateway;
        this.properties = properties;
    }

    /**
     * Search Google Drive and extract text from top files.
     * Returns a formatted context string ready for injection into the pipeline.
     */
    public DriveSearchResult searchAndExtract(String userId, String query, int maxExtractFiles) {
        log.info("[DRIVE] Searching for user '{}': query='{}'", userId, query);

        String accessToken = tokenService.getValidAccessToken(userId);

        List<DriveFileResult> files = driveClient.searchFiles(
                accessToken, query, properties.getMaxSearchResults());

        if (files.isEmpty()) {
            log.info("[DRIVE] No files found");
            return new DriveSearchResult(List.of(), null);
        }

        List<DriveDocumentResult> extracted = files.stream()
                .limit(maxExtractFiles)
                .map(f -> safeExtract(accessToken, f))
                .filter(Objects::nonNull)
                .filter(r -> r.extractedText() != null && !r.extractedText().isBlank())
                .toList();

        String driveDocumentContext = buildDriveContext(extracted);

        log.info("[DRIVE] Extracted text from {} files", extracted.size());
        return new DriveSearchResult(extracted, driveDocumentContext);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private DriveDocumentResult safeExtract(String accessToken, DriveFileResult file) {
        try {
            return extractText(accessToken, file);
        } catch (Exception e) {
            log.warn("[DRIVE] Failed to extract '{}': {}", file.name(), e.getMessage());
            return null;
        }
    }

    private DriveDocumentResult extractText(String accessToken, DriveFileResult file) {
        String text;

        if (GOOGLE_WORKSPACE_TYPES.contains(file.mimeType())) {
            // Native export — no OCR needed
            String exportMimeType = getExportMimeType(file.mimeType());
            text = driveClient.exportAsText(accessToken, file.fileId(), exportMimeType);
            log.info("[DRIVE] Exported '{}' ({}) — {} chars",
                    file.name(), exportMimeType, text != null ? text.length() : 0);
        } else if (OCR_TYPES.contains(file.mimeType())) {
            // Download + OCR via Docling
            byte[] content = driveClient.downloadFile(accessToken, file.fileId());
            DocumentParseResult result = documentGateway.parse(content, file.name());
            text = result.getExtractedText();
            log.info("[DRIVE] OCR'd '{}' — {} chars", file.name(), text != null ? text.length() : 0);
        } else {
            log.debug("[DRIVE] Unsupported MIME type '{}' for file '{}'", file.mimeType(), file.name());
            return null;
        }

        // Truncate to max content length
        if (text != null && text.length() > properties.getMaxContentLength()) {
            text = text.substring(0, properties.getMaxContentLength()) + "\n[...tronque]";
        }

        return new DriveDocumentResult(
                file.fileId(), file.name(), file.mimeType(),
                file.webViewLink(), text
        );
    }

    private String getExportMimeType(String googleMimeType) {
        return switch (googleMimeType) {
            case MIME_GOOGLE_DOC -> "text/plain";
            case MIME_GOOGLE_SHEET -> "text/csv";
            case MIME_GOOGLE_SLIDES -> "text/plain";
            default -> "text/plain";
        };
    }

    private String buildDriveContext(List<DriveDocumentResult> results) {
        if (results.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            DriveDocumentResult doc = results.get(i);
            sb.append("### Document Drive ").append(i + 1).append(" : ").append(doc.fileName());
            if (doc.webViewLink() != null) {
                sb.append(" (").append(doc.webViewLink()).append(")");
            }
            sb.append("\n");
            sb.append(doc.extractedText());
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }
}
