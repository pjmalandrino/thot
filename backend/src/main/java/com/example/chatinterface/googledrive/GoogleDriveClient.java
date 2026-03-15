package com.example.chatinterface.googledrive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Low-level Google Drive API v3 client.
 * Handles search, export (Workspace docs), and download (binary files).
 */
@Component
public class GoogleDriveClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveClient.class);
    private static final String DRIVE_API_BASE = "https://www.googleapis.com/drive/v3";

    private final RestClient restClient;

    public GoogleDriveClient() {
        this.restClient = RestClient.builder()
                .baseUrl(DRIVE_API_BASE)
                .build();
    }

    /**
     * Search Drive files using fullText + name matching.
     */
    public List<DriveFileResult> searchFiles(String accessToken, String query, int maxResults) {
        String driveQuery = "fullText contains '" + escapeQuery(query)
                + "' and trashed = false";
        String fields = "files(id,name,mimeType,modifiedTime,webViewLink,size)";

        log.info("[DRIVE-CLIENT] Searching: q='{}', maxResults={}", driveQuery, maxResults);

        FileListResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/files")
                        .queryParam("q", driveQuery)
                        .queryParam("fields", fields)
                        .queryParam("pageSize", maxResults)
                        .queryParam("orderBy", "relevance")
                        .build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(FileListResponse.class);

        if (response == null || response.files == null) {
            log.warn("[DRIVE-CLIENT] No results returned");
            return List.of();
        }

        log.info("[DRIVE-CLIENT] Found {} files", response.files.size());
        return response.files.stream()
                .map(f -> new DriveFileResult(f.id, f.name, f.mimeType,
                        f.modifiedTime, f.webViewLink, f.size))
                .toList();
    }

    /**
     * Export a Google Workspace document (Docs, Sheets, Slides) as text.
     */
    public String exportAsText(String accessToken, String fileId, String exportMimeType) {
        log.info("[DRIVE-CLIENT] Exporting file '{}' as '{}'", fileId, exportMimeType);

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/files/{fileId}/export")
                        .queryParam("mimeType", exportMimeType)
                        .build(fileId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(String.class);
    }

    /**
     * Download a binary file (PDFs, images, etc.) as raw bytes.
     */
    public byte[] downloadFile(String accessToken, String fileId) {
        log.info("[DRIVE-CLIENT] Downloading file '{}'", fileId);

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/files/{fileId}")
                        .queryParam("alt", "media")
                        .build(fileId))
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(byte[].class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private String escapeQuery(String query) {
        // Escape single quotes for the Drive query syntax
        return query.replace("'", "\\'");
    }

    // ── Response DTOs ───────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FileListResponse {
        @JsonProperty("files")
        public List<FileResource> files;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class FileResource {
        @JsonProperty("id")
        public String id;
        @JsonProperty("name")
        public String name;
        @JsonProperty("mimeType")
        public String mimeType;
        @JsonProperty("modifiedTime")
        public String modifiedTime;
        @JsonProperty("webViewLink")
        public String webViewLink;
        @JsonProperty("size")
        public Long size;
    }
}
