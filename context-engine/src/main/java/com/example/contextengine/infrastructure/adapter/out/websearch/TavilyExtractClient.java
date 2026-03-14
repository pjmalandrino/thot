package com.example.contextengine.infrastructure.adapter.out.websearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public class TavilyExtractClient {

    private static final Logger log = LoggerFactory.getLogger(TavilyExtractClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public TavilyExtractClient(String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.tavily.com")
                .build();
    }

    public List<ExtractResult> extract(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }

        try {
            log.info("[TAVILY-EXTRACT] Calling extract API for {} URLs", urls.size());
            Map<String, Object> body = Map.of(
                    "api_key", apiKey,
                    "urls", urls
            );

            ExtractResponse response = restClient.post()
                    .uri("/extract")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ExtractResponse.class);
            log.info("[TAVILY-EXTRACT] API response received");

            if (response == null || response.results == null) {
                log.warn("Tavily Extract returned null response for URLs: {}", urls);
                return List.of();
            }

            if (response.failedResults != null && !response.failedResults.isEmpty()) {
                response.failedResults.forEach(f ->
                        log.warn("[TAVILY-EXTRACT] Failed for {}: {}", f.url, f.error));
            }

            for (ExtractResult r : response.results) {
                int len = r.rawContent != null ? r.rawContent.length() : 0;
                log.info("[TAVILY-EXTRACT] OK {} ({} chars)", r.url, len);
            }

            return response.results;

        } catch (Exception e) {
            log.error("Tavily Extract API call failed for URLs: {}", urls, e);
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractResponse {
        @JsonProperty("results")
        public List<ExtractResult> results;

        @JsonProperty("failed_results")
        public List<FailedResult> failedResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractResult {
        @JsonProperty("url")
        public String url;

        @JsonProperty("raw_content")
        public String rawContent;

        public String getUrl() { return url; }
        public String getRawContent() { return rawContent; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FailedResult {
        @JsonProperty("url")
        public String url;

        @JsonProperty("error")
        public String error;
    }
}
