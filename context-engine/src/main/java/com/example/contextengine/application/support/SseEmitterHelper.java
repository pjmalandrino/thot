package com.example.contextengine.application.support;

import com.example.contextengine.domain.model.ContextAnalysis;
import com.example.contextengine.domain.model.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * Centralizes all SSE event emission and JSON utility methods
 * shared across StreamingController, DeepResearchOrchestrator, and LabOrchestrator.
 */
@Component
public class SseEmitterHelper {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterHelper.class);
    private final ObjectMapper mapper = new ObjectMapper();

    // ── Core SSE methods (used by all 3 classes) ─────────────────────────────

    public void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            log.warn("[SSE] Failed to send event '{}': {}", eventName, e.getMessage());
        }
    }

    public String quote(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }

    public String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public String buildDonePayload(String response, String thinking,
                                    List<SearchResult> sources, boolean autoWebSearchTriggered) {
        try {
            var node = mapper.createObjectNode();
            node.put("response", response);
            if (thinking != null && !thinking.isBlank()) node.put("thinking", thinking);
            node.put("autoWebSearchTriggered", autoWebSearchTriggered);
            var sourcesArray = node.putArray("sources");
            for (SearchResult s : sources) {
                var sourceNode = sourcesArray.addObject();
                sourceNode.put("citationId", s.citationId());
                sourceNode.put("sourceUrl", s.sourceUrl());
                sourceNode.put("sourceTitle", s.sourceTitle());
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"response\":" + quote(response) + ",\"sources\":[]}";
        }
    }

    // ── Orchestrator-specific SSE methods ────────────────────────────────────

    public void emitStepEvent(SseEmitter emitter, String stepId, String status,
                               String label, String detail) {
        try {
            var node = mapper.createObjectNode();
            node.put("stepId", stepId);
            node.put("status", status);
            if (label != null) node.put("label", label);
            if (detail != null) node.put("detail", detail);
            sendEvent(emitter, "step", mapper.writeValueAsString(node));
        } catch (Exception e) {
            // Fallback with proper escaping via quote()
            StringBuilder json = new StringBuilder("{");
            json.append("\"stepId\":").append(quote(stepId)).append(",");
            json.append("\"status\":").append(quote(status));
            if (label != null) json.append(",\"label\":").append(quote(label));
            if (detail != null) json.append(",\"detail\":").append(quote(detail));
            json.append("}");
            sendEvent(emitter, "step", json.toString());
        }
    }

    public void emitSourcesEvent(SseEmitter emitter, List<SearchResult> sources) {
        try {
            var node = mapper.createObjectNode();
            var sourcesArray = node.putArray("sources");
            for (SearchResult s : sources) {
                var sourceNode = sourcesArray.addObject();
                sourceNode.put("citationId", s.citationId());
                sourceNode.put("sourceUrl", s.sourceUrl());
                sourceNode.put("sourceTitle", s.sourceTitle());
            }
            sendEvent(emitter, "sources", mapper.writeValueAsString(node));
        } catch (Exception e) {
            sendEvent(emitter, "sources", "{\"sources\":[]}");
        }
    }

    public void emitClarificationEvent(SseEmitter emitter, ContextAnalysis analysis) {
        try {
            var node = mapper.createObjectNode();
            node.put("message", analysis.getClarificationMessage());
            var sugArray = node.putArray("suggestions");
            if (analysis.getSuggestions() != null) {
                analysis.getSuggestions().forEach(sugArray::add);
            }
            sendEvent(emitter, "clarification", mapper.writeValueAsString(node));
        } catch (Exception e) {
            sendEvent(emitter, "clarification",
                    "{\"message\":" + quote(analysis.getClarificationMessage()) + "}");
        }
    }

    public void emitThinking(SseEmitter emitter, String content) {
        sendEvent(emitter, "thinking", "{\"content\":" + quote(content) + "}");
    }

    // ── String utilities ─────────────────────────────────────────────────────

    public String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public String extractJson(String raw) {
        if (raw == null) return "{}";
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}
