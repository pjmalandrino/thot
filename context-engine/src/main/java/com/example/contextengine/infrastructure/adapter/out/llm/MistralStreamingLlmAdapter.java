package com.example.contextengine.infrastructure.adapter.out.llm;

import com.example.contextengine.domain.model.AnswerChunk;
import com.example.contextengine.domain.model.ConversationMessage;
import com.example.contextengine.domain.model.ThinkingChunk;
import com.example.contextengine.domain.port.out.StreamingLlmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Direct HTTP streaming adapter for Mistral Magistral models.
 * Handles the structured thinking/text content chunks from the Magistral API.
 * Includes retry logic for 429 rate limits.
 */
public class MistralStreamingLlmAdapter implements StreamingLlmPort {

    private static final Logger log = LoggerFactory.getLogger(MistralStreamingLlmAdapter.class);
    private static final String MISTRAL_API_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final int MAX_RETRIES = 3;

    private final String apiKey;
    private final String modelName;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public MistralStreamingLlmAdapter(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void streamThinkAndAnswer(String systemPrompt, String userMessage,
                                     Consumer<ThinkingChunk> onThinking,
                                     Consumer<AnswerChunk> onAnswer,
                                     Consumer<Throwable> onError,
                                     Runnable onComplete) {
        streamThinkAndAnswer(systemPrompt, userMessage, List.of(), onThinking, onAnswer, onError, onComplete);
    }

    @Override
    public void streamThinkAndAnswer(String systemPrompt, String userMessage,
                                     List<ConversationMessage> conversationHistory,
                                     Consumer<ThinkingChunk> onThinking,
                                     Consumer<AnswerChunk> onAnswer,
                                     Consumer<Throwable> onError,
                                     Runnable onComplete) {
        try {
            String requestBody = buildRequestBody(systemPrompt, userMessage, conversationHistory);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MISTRAL_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<java.io.InputStream> response = sendWithRetry(request);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;
                        processChunk(data, onThinking, onAnswer);
                    }
                }
            }

            onComplete.run();
        } catch (Exception e) {
            log.error("[MISTRAL-STREAM] Error during streaming: {}", e.getMessage(), e);
            onError.accept(e);
        }
    }

    private HttpResponse<java.io.InputStream> sendWithRetry(HttpRequest request) throws Exception {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 429 && attempt < MAX_RETRIES) {
                String retryAfter = response.headers()
                        .firstValue("Retry-After").orElse("2");
                long waitMs;
                try {
                    waitMs = Long.parseLong(retryAfter) * 1000;
                } catch (NumberFormatException e) {
                    waitMs = 2000;
                }
                waitMs = Math.min(waitMs, 10_000);
                log.warn("[MISTRAL-STREAM] Rate limited (429), retrying in {}ms (attempt {}/{})",
                        waitMs, attempt + 1, MAX_RETRIES);
                Thread.sleep(waitMs);
                continue;
            }

            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("Mistral API error " + response.statusCode() + ": " + errorBody);
            }

            return response;
        }
        throw new RuntimeException("Mistral API: max retries exceeded (429)");
    }

    private String buildRequestBody(String systemPrompt, String userMessage,
                                     List<ConversationMessage> conversationHistory) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", modelName);
        root.put("stream", true);

        ArrayNode messages = root.putArray("messages");

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
        }

        // Insert conversation history between system and user message
        if (conversationHistory != null) {
            for (ConversationMessage msg : conversationHistory) {
                ObjectNode histMsg = messages.addObject();
                histMsg.put("role", msg.role());
                histMsg.put("content", msg.content());
            }
        }

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        return root.toString();
    }

    private void processChunk(String data, Consumer<ThinkingChunk> onThinking,
                              Consumer<AnswerChunk> onAnswer) {
        try {
            JsonNode chunk = mapper.readTree(data);
            JsonNode choices = chunk.get("choices");
            if (choices == null || choices.isEmpty()) return;

            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) return;

            // Magistral -2509+ format: content is an array of typed objects
            JsonNode content = delta.get("content");
            if (content != null) {
                if (content.isArray()) {
                    // Structured format: [{type: "thinking", thinking: [...]}, {type: "text", text: "..."}]
                    for (JsonNode block : content) {
                        String type = block.has("type") ? block.get("type").asText() : "";
                        if ("thinking".equals(type)) {
                            JsonNode thinkingContent = block.get("thinking");
                            if (thinkingContent != null && thinkingContent.isArray()) {
                                for (JsonNode t : thinkingContent) {
                                    String text = t.has("text") ? t.get("text").asText() : "";
                                    if (!text.isEmpty()) onThinking.accept(new ThinkingChunk(text));
                                }
                            }
                        } else if ("text".equals(type)) {
                            String text = block.has("text") ? block.get("text").asText() : "";
                            if (!text.isEmpty()) onAnswer.accept(new AnswerChunk(text));
                        }
                    }
                } else if (content.isTextual()) {
                    // Older or simpler streaming format: content is a plain string
                    String text = content.asText();
                    if (!text.isEmpty()) onAnswer.accept(new AnswerChunk(text));
                }
            }
        } catch (Exception e) {
            log.debug("[MISTRAL-STREAM] Failed to parse chunk: {}", data, e);
        }
    }
}
