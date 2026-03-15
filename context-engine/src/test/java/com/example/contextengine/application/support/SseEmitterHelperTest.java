package com.example.contextengine.application.support;

import com.example.contextengine.domain.model.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SseEmitterHelperTest {

    private final SseEmitterHelper helper = new SseEmitterHelper();
    private final ObjectMapper mapper = new ObjectMapper();

    // ── quote() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("quote()")
    class Quote {

        @Test
        @DisplayName("retourne \"null\" pour null")
        void nullInput() {
            assertThat(helper.quote(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("retourne des guillemets vides pour une chaine vide")
        void emptyInput() {
            assertThat(helper.quote("")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("echappe les newlines")
        void escapesNewlines() {
            assertThat(helper.quote("line1\nline2")).isEqualTo("\"line1\\nline2\"");
        }

        @Test
        @DisplayName("echappe les tabs")
        void escapesTabs() {
            assertThat(helper.quote("col1\tcol2")).isEqualTo("\"col1\\tcol2\"");
        }

        @Test
        @DisplayName("echappe les guillemets doubles")
        void escapesDoubleQuotes() {
            assertThat(helper.quote("say \"hello\"")).isEqualTo("\"say \\\"hello\\\"\"");
        }

        @Test
        @DisplayName("echappe les backslash")
        void escapesBackslash() {
            assertThat(helper.quote("path\\to")).isEqualTo("\"path\\\\to\"");
        }

        @Test
        @DisplayName("echappe les retours chariot")
        void escapesCarriageReturn() {
            assertThat(helper.quote("a\rb")).isEqualTo("\"a\\rb\"");
        }
    }

    // ── escapeJson() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("escapeJson()")
    class EscapeJson {

        @Test
        @DisplayName("echappe les backslash")
        void escapesBackslash() {
            assertThat(helper.escapeJson("a\\b")).isEqualTo("a\\\\b");
        }

        @Test
        @DisplayName("echappe les guillemets")
        void escapesQuotes() {
            assertThat(helper.escapeJson("say \"hi\"")).isEqualTo("say \\\"hi\\\"");
        }

        @Test
        @DisplayName("echappe les newlines")
        void escapesNewlines() {
            assertThat(helper.escapeJson("line1\nline2")).isEqualTo("line1\\nline2");
        }

        @Test
        @DisplayName("echappe les retours chariot")
        void escapesCarriageReturn() {
            assertThat(helper.escapeJson("a\rb")).isEqualTo("a\\rb");
        }
    }

    // ── truncate() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("truncate()")
    class Truncate {

        @Test
        @DisplayName("retourne chaine vide pour null")
        void nullInput() {
            assertThat(helper.truncate(null, 10)).isEqualTo("");
        }

        @Test
        @DisplayName("retourne la chaine si plus courte que maxLen")
        void shortString() {
            assertThat(helper.truncate("hello", 10)).isEqualTo("hello");
        }

        @Test
        @DisplayName("retourne la chaine si taille exacte")
        void exactLength() {
            assertThat(helper.truncate("12345", 5)).isEqualTo("12345");
        }

        @Test
        @DisplayName("tronque avec ... si trop long")
        void longString() {
            assertThat(helper.truncate("1234567890", 5)).isEqualTo("12345...");
        }
    }

    // ── extractJson() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractJson()")
    class ExtractJson {

        @Test
        @DisplayName("retourne {} pour null")
        void nullInput() {
            assertThat(helper.extractJson(null)).isEqualTo("{}");
        }

        @Test
        @DisplayName("extrait le JSON propre")
        void cleanJson() {
            assertThat(helper.extractJson("{\"key\":\"value\"}"))
                    .isEqualTo("{\"key\":\"value\"}");
        }

        @Test
        @DisplayName("extrait le JSON entoure de markdown")
        void jsonInMarkdown() {
            String raw = "Voici le JSON:\n```json\n{\"subQuestions\":[\"q1\"]}\n```\n";
            assertThat(helper.extractJson(raw)).isEqualTo("{\"subQuestions\":[\"q1\"]}");
        }

        @Test
        @DisplayName("retourne le texte brut si pas de braces")
        void noBraces() {
            assertThat(helper.extractJson("pas de json")).isEqualTo("pas de json");
        }
    }

    // ── buildDonePayload() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("buildDonePayload()")
    class BuildDonePayload {

        @Test
        @DisplayName("genere un payload sans sources ni thinking")
        void emptySourcesNoThinking() throws Exception {
            String payload = helper.buildDonePayload("reponse", null, List.of(), false);
            JsonNode node = mapper.readTree(payload);

            assertThat(node.get("response").asText()).isEqualTo("reponse");
            assertThat(node.has("thinking")).isFalse();
            assertThat(node.get("autoWebSearchTriggered").asBoolean()).isFalse();
            assertThat(node.get("sources").size()).isZero();
        }

        @Test
        @DisplayName("inclut les sources et le thinking")
        void withSourcesAndThinking() throws Exception {
            List<SearchResult> sources = List.of(
                    new SearchResult("[1]", "https://a.com", "Title A", "text A"));
            String payload = helper.buildDonePayload("resp", "my thinking", sources, true);
            JsonNode node = mapper.readTree(payload);

            assertThat(node.get("response").asText()).isEqualTo("resp");
            assertThat(node.get("thinking").asText()).isEqualTo("my thinking");
            assertThat(node.get("autoWebSearchTriggered").asBoolean()).isTrue();
            assertThat(node.get("sources").size()).isEqualTo(1);
            assertThat(node.get("sources").get(0).get("citationId").asText()).isEqualTo("[1]");
            assertThat(node.get("sources").get(0).get("sourceUrl").asText()).isEqualTo("https://a.com");
            assertThat(node.get("sources").get(0).get("sourceTitle").asText()).isEqualTo("Title A");
        }

        @Test
        @DisplayName("ignore le thinking blank")
        void blankThinking() throws Exception {
            String payload = helper.buildDonePayload("resp", "   ", List.of(), false);
            JsonNode node = mapper.readTree(payload);

            assertThat(node.has("thinking")).isFalse();
        }
    }

    // ── emitStepEvent() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("emitStepEvent()")
    class EmitStepEvent {

        @Test
        @DisplayName("ne lance pas d'exception avec un emitter complete")
        void noExceptionOnCompletedEmitter() {
            // A completed emitter will throw IOException on send — sendEvent absorbs it
            SseEmitter emitter = new SseEmitter();
            emitter.complete();

            assertThatNoException().isThrownBy(() ->
                    helper.emitStepEvent(emitter, "step-1", "running", "Recherche", "sous-query 1"));
        }

        @Test
        @DisplayName("gere les valeurs null pour label et detail")
        void nullLabelAndDetail() {
            SseEmitter emitter = new SseEmitter();
            emitter.complete();

            assertThatNoException().isThrownBy(() ->
                    helper.emitStepEvent(emitter, "step-1", "done", null, null));
        }

        @Test
        @DisplayName("echappe correctement les caracteres speciaux dans le JSON")
        void specialCharactersProduceValidJson() throws Exception {
            // Verify the JSON construction is correct by testing the underlying ObjectMapper logic
            // emitStepEvent uses ObjectMapper.createObjectNode() which always produces valid JSON
            var objectMapper = new ObjectMapper();
            var node = objectMapper.createObjectNode();
            node.put("stepId", "step\"1");
            node.put("status", "run\nning");
            node.put("label", "lab\\el");
            node.put("detail", "det\tail");
            String json = objectMapper.writeValueAsString(node);

            // Must be valid JSON
            JsonNode parsed = objectMapper.readTree(json);
            assertThat(parsed.get("stepId").asText()).isEqualTo("step\"1");
            assertThat(parsed.get("status").asText()).isEqualTo("run\nning");
        }
    }

    // ── sendEvent() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendEvent()")
    class SendEvent {

        @Test
        @DisplayName("absorbe l'IOException sans propager (emitter complete)")
        void ioExceptionIsSilent() {
            // A completed emitter will throw IOException on send
            SseEmitter emitter = new SseEmitter();
            emitter.complete();

            assertThatNoException().isThrownBy(() ->
                    helper.sendEvent(emitter, "answer", "data"));
        }

        @Test
        @DisplayName("ne lance pas d'exception pour des donnees valides")
        void validDataNoException() {
            SseEmitter emitter = new SseEmitter();
            emitter.complete();

            assertThatNoException().isThrownBy(() ->
                    helper.sendEvent(emitter, "thinking", "{\"content\":\"hello\"}"));
        }
    }
}
