package com.example.chatinterface.contextengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ContextEngineClient {

    private static final Logger log = LoggerFactory.getLogger(ContextEngineClient.class);

    private final RestClient restClient;

    public ContextEngineClient(@Value("${context-engine.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public ContextEngineResponse analyze(ContextEngineRequest request) {
        log.info("[CONTEXT-ENGINE] Calling analyze: prompt='{}'", request.getPrompt());
        try {
            ContextEngineResponse response = restClient.post()
                    .uri("/api/context/analyze")
                    .body(request)
                    .retrieve()
                    .body(ContextEngineResponse.class);
            log.info("[CONTEXT-ENGINE] Response: status={}", response != null ? response.getStatus() : "null");
            return response != null ? response : ContextEngineResponse.failOpen();
        } catch (Exception e) {
            log.error("[CONTEXT-ENGINE] Call failed, failing open: {}", e.getMessage());
            return ContextEngineResponse.failOpen();
        }
    }
}
