package com.example.chatinterface.contextengine;

import com.example.chatinterface.shared.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Consumes SSE from Context Engine streaming endpoints and proxies events
 * to the client's SseEmitter.
 * Uses POST with JSON body to support large documentContext payloads.
 */
@Component
public class StreamingContextEngineClient {

    private static final Logger log = LoggerFactory.getLogger(StreamingContextEngineClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public StreamingContextEngineClient(@Value("${context-engine.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Proxies SSE events from context-engine to the client emitter.
     * Sends params as POST JSON body (supports large documentContext).
     * Calls onDone when a "done" event is received with the raw JSON data.
     */
    public void proxyStream(String endpoint, Map<String, Object> params,
                            SseEmitter clientEmitter,
                            Consumer<String> onDone,
                            Consumer<Throwable> onError) {
        boolean receivedData = false;
        try {
            String url = baseUrl + endpoint;
            String jsonBody = mapper.writeValueAsString(params);

            log.info("[SSE-PROXY] POST to {} (body {} chars)", url, jsonBody.length());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            log.info("[SSE-PROXY] Response status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new ServiceException("Context Engine returned " + response.statusCode() + ": " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                String eventName = null;
                while ((line = readLineSafe(reader)) != null) {
                    if (line.startsWith("event:")) {
                        eventName = line.substring(6).trim();
                    } else if (line.startsWith("data:") && eventName != null) {
                        String data = line.substring(5).trim();
                        receivedData = true;

                        // Proxy the event to client
                        try {
                            clientEmitter.send(SseEmitter.event()
                                    .name(eventName)
                                    .data(data));
                        } catch (IOException e) {
                            log.warn("[SSE-PROXY] Client disconnected");
                            return;
                        }

                        if ("done".equals(eventName)) {
                            onDone.accept(data);
                        }

                        eventName = null;
                    }
                }
            }
            log.info("[SSE-PROXY] Stream ended normally (receivedData={})", receivedData);
        } catch (Exception e) {
            if (receivedData) {
                log.info("[SSE-PROXY] Stream ended after forwarding events (EOF: {})", e.getMessage());
            } else {
                log.error("[SSE-PROXY] Stream error: {}", e.getMessage(), e);
                onError.accept(e);
            }
        }
    }

    private String readLineSafe(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException e) {
            log.debug("[SSE-PROXY] Read terminated: {}", e.getMessage());
            return null;
        }
    }
}
