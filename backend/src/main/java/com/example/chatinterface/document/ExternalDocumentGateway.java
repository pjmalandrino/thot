package com.example.chatinterface.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/**
 * Calls the external Python FastAPI document parser (Docling).
 * Uses OkHttpClient for reliable multipart/form-data construction.
 */
@Component
public class ExternalDocumentGateway implements DocumentGateway {

    private static final Logger log = LoggerFactory.getLogger(ExternalDocumentGateway.class);
    private static final okhttp3.MediaType OCTET_STREAM =
            okhttp3.MediaType.parse("application/octet-stream");

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExternalDocumentGateway(
            @Value("${document-parser.base-url:http://localhost:8000}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(120))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentParseResult parse(MultipartFile file) {
        log.info("[DOC-PARSE] Sending '{}' ({} bytes) to parser",
                file.getOriginalFilename(), file.getSize());
        try {
            byte[] content = file.getBytes();
            String filename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "file";

            RequestBody multipart = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename, RequestBody.create(content, OCTET_STREAM))
                    .build();

            Request request = new Request.Builder()
                    .url(baseUrl + "/parse")
                    .post(multipart)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "no body";
                    throw new RuntimeException("Parser returned " + response.code() + ": " + errorBody);
                }
                String responseBody = response.body().string();
                ExternalParseResponse parsed = objectMapper.readValue(responseBody, ExternalParseResponse.class);

                log.info("[DOC-PARSE] Parsed '{}' — {} chars, {} pages",
                        parsed.filename, parsed.charCount, parsed.pageCount);

                return new DocumentParseResult(
                        parsed.filename,
                        parsed.contentType,
                        parsed.pageCount,
                        parsed.charCount,
                        parsed.extractedText
                );
            }
        } catch (IOException e) {
            log.error("[DOC-PARSE] Failed to parse document: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Document parsing failed: " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ExternalParseResponse {
        @JsonProperty("filename")
        public String filename;
        @JsonProperty("content_type")
        public String contentType;
        @JsonProperty("page_count")
        public Integer pageCount;
        @JsonProperty("char_count")
        public int charCount;
        @JsonProperty("extracted_text")
        public String extractedText;
    }
}
